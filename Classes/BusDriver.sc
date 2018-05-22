/*

Streams values from sclang to a bus.
currently: audio rate and single channel only.

*/

SimpleBusDriver {

	var <>func, <bus, <numFrames;
	var task, server, groupID;
	var delta, halfBlockDur, synthName;

	*new { |func, bus, numFrames = 512|
		^super.newCopyArgs(func, bus, numFrames).init
	}

	init {
		server = bus.server;
		if(server.serverRunning.not) {
			Error("server % not running".format(server)).throw
		};
		this.sendSynthDefs;
		this.startGroup;
		delta = numFrames / server.sampleRate;
		halfBlockDur = server.options.blockSize * delta * 0.1;
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


	}

	startGroup {
		groupID = server.nextNodeID;
		server.sendMsg("/g_new", groupID, 0, 1); // later, target, doneAction?
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

	initSynths { |n = 1|
		this.freeSynths;
		server.sendMsg("/g_new", groupID, 1, 1);
		this.addSynth(true);
		(n - 1).do { this.addSynth(false) };
	}

	resumeNextSynth {}

	run {

		task = Task {
			this.initSynths;
			loop {
				this.addSynth;
				this.resumeNextSynth;
				delta.wait;
			}
		}.play(SystemClock);
	}

	freeSynths {
		server.sendMsg("/g_freeAll", groupID);
	}

	stop {
		task.stop;
		task = nil;
		this.freeSynths;
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

	initSynths { |n = 1|
		this.freeSynths;
		server.sendMsg("/g_new", groupID, 1, 1);
		n.do { this.addSynth };
	}

	run {
		this.stop;
		task = Task {
			this.initSynths;
			loop {
				this.addSynth;
				this.resumeNextSynth;
				delta.wait;
			}
		}.play(SystemClock);
	}

	freeSynths {
		server.sendMsg("/g_freeAll", groupID);
		synths = [];
	}

}


