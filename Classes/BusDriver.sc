/*

Streams values from sclang to a bus.
currently: audio rate and single channel only.

*/

BusDriver {

	var <>func, <bus, <numFrames, <>latency;
	var synths, task, nodeWatcher, server, groupID;

	var prDelta, sampleDur, halfBlockDur, synthName;

	var <>queueMaxSize = 8, <>queueMinSize = 2;

	*new { |func, bus = 0, numFrames = 512, latency|
		^super.newCopyArgs(func, bus.asBus, numFrames, latency).init
	}

	init {
		server = bus.server;
		if(server.serverRunning.not) {
			Error("server % not running".format(server)).throw
		};
		latency = latency ?? { server.latency };
		this.sendSynthDefs;
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
		server.sendBundle(latency,
			["/s_new", synthName, id, 1, groupID] ++  ["array", array, "out", bus].asOSCArgArray,
			[12, id, running.binaryValue] // paused
		);
		synths.add(id);
		^id
	}

	initGroup {
		groupID = groupID ?? { server.nextNodeID };
		server.sendMsg("/g_new", groupID, 1, 1);
	}

	addFirstSynth {
		this.addSynth(true); // add a synth that is not paused to ignite the chain
	}

	initSynths {
		this.freeSynths;
		this.initGroup;
		this.addFirstSynth;
		queueMinSize.do { this.addSynth(false) };
	}

	run {
		this.stop;
		this.startListen;
		task = Task {
			this.initSynths;
			loop {
				if(synths.size < queueMaxSize) { this.addSynth };
				this.prDelta.wait;
			}
		}.play(SystemClock);
	}

	freeSynths {
		groupID !? {
			server.sendBundle(server.latency,
				['/error', -1],
				["/g_freeAll", groupID],
				["/n_free", groupID],
				['/error', -2]
			);
			groupID = nil;
		};
		synths = Set.new
	}

	stop {
		task.stop;
		task = nil;
		this.freeSynths;
	}

	startListen {
		nodeWatcher = OSCFunc({ |msg|
			var nodeID = msg[1];
			synths.remove(nodeID);
		}, "/n_end", server.addr, nil, [nil, groupID])
	}

	prDelta {
		^numFrames * sampleDur
	}

}

/*

the function should return events with \value and \delta

*/

EventBusDriver : BusDriver {

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

		server.sendBundle(latency,
			["/s_new", synthName, id, 1, groupID] ++  ["values", values, "times", times, "out", bus].asOSCArgArray,
			[12, id, running.binaryValue] // paused
		);
		^id
	}

	prDelta {
		^nextDuration * sampleDur
	}
}



