/*

Streams values from sclang to a bus.
currently: audio rate and single channel only.

*/

SimpleBusDriver {

	var <>func, <bus, <numFrames;
	var task, server, groupID;
	var prDelta, sampleDur, halfBlockDur, synthName;

	var <>initSynthNumber = 2;

	*new { |func, bus = 0, numFrames = 512|
		^super.newCopyArgs(func, bus.asBus, numFrames).init
	}

	init {
		server = bus.server;
		if(server.serverRunning.not) {
			Error("server % not running".format(server)).throw
		};
		this.sendSynthDefs;
		this.startGroup;
		sampleDur = server.sampleRate.reciprocal;
		halfBlockDur = server.options.blockSize * sampleDur * 0.1;
	}


	doneAction {
		^Done.freeSelfResumeNext
	}

	namePrefix {
		^this.class.name
	}

	sendSynthDefs {

		synthName =  this.namePrefix ++ "_" ++ bus.numChannels;

		SynthDef(synthName, { |out = 0|
			var array, signal;
			array = \array.ir(0 ! numFrames);
			signal = Duty.ar(SampleDur.ir, 0, Dseq(array, 1), doneAction:this.doneAction);
			ReplaceOut.ar(out, signal)
		}).load(server);

		"written synthdef for % called %".format(this.class, synthName).postln;

	}

	addSynth { |running = false|
		var id = server.nextNodeID;
		var array = Array.fill(numFrames, func);
		server.sendBundle(server.latency,
			["/s_new", synthName, id, 1, groupID] ++  ["array", array, "out", bus].asOSCArgArray,
			[12, id, running.binaryValue] // paused
		);
		^id
	}

	startGroup {
		groupID = server.nextNodeID;
		server.sendMsg("/g_new", groupID, 0, 1); // later, target, doneAction?
	}

	initSynths {
		this.freeSynths;
		server.sendMsg("/g_new", groupID, 1, 1);
		this.addSynth(true);
		(initSynthNumber - 1).do { this.addSynth(false) };
	}

	resumeNextSynth {}

	run {

		task = Task {
			this.initSynths;
			loop {
				this.addSynth;
				this.resumeNextSynth;
				this.prDelta.wait;
			}
		}.play(SystemClock);
	}

	freeSynths {
		server.sendMsg("/g_freeAll", groupID);
		server.sendMsg("/n_free", groupID);
	}

	stop {
		task.stop;
		task = nil;
		this.freeSynths;
	}

	prDelta {
		^numFrames * sampleDur
	}

}

/*

the function should return events with \value and \delta

*/

EventBusDriver : SimpleBusDriver {

	var nextDuration;



	sendSynthDefs {

		synthName =  this.namePrefix ++ "_" ++ bus.numChannels;

		SynthDef(synthName, { |out = 0|
			var values, times, signal;
			values = \values.ir(0 ! numFrames);
			times = \times.ir(1 ! numFrames).max(1);
			signal = Duty.ar(SampleDur.ir * Dseq(times, 1), 0, Dseq(values, 1), doneAction:this.doneAction);
			ReplaceOut.ar(out, signal)
		}).load(server);

		"written synthdef for % called %".format(this.class, synthName).postln;

	}

	addSynth { |running = false|
		var id, events, values, times;
		nextDuration = 0;
		id = server.nextNodeID;
		events = Array.fill(numFrames, func);
		values = events.collect { |x|
			x[\value]
		};
		times = events.collect { |x|
			var dur = x.delta;
			nextDuration = nextDuration + dur;
			dur
		};

		server.sendBundle(server.latency,
			["/s_new", synthName, id, 1, groupID] ++  ["values", values, "times", times, "out", bus].asOSCArgArray,
			[12, id, running.binaryValue] // paused
		);
		^id
	}

	prDelta {
		^nextDuration * sampleDur
	}
}


/*

Keeps a list of synths and does the resuming sclang side

*/



BusDriver : SimpleBusDriver {

	var synths;


	doneAction {
		^Done.freeSelf
	}

	addSynth { |running = false|
		var id = super.addSynth(running);
		synths = synths.add(id);
	}

	resumeNextSynth {
		var id = synths.removeAt(0);
		if(id.isNil) { "BusDriver: underrun queue".warn } {
			server.sendBundle(server.latency + halfBlockDur, [12, id, 1])
		};
	}

	initSynths {
		this.freeSynths;
		server.sendMsg("/g_new", groupID, 1, 1);
		initSynthNumber.do { this.addSynth };
	}

	run {
		this.stop;
		task = Task {
			this.initSynths;
			loop {
				this.addSynth;
				this.resumeNextSynth;
				this.prDelta.wait;
			}
		}.play(SystemClock);
	}

	freeSynths {
		server.sendMsg("/g_freeAll", groupID);
		synths = [];
	}

}


