/*

Streams values from sclang to a bus.
currently: audio rate and single channel only.

todo: link lats with first values!

*/

BusDriver {

	var <>func, <bus, <numFrames, <>latency;
	var <synths, nodeWatcher, server, groupID;
	var <>timeScale = 1;

	var sampleDur, synthName;

	var <>queueSize = 20;

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
		synths = IdentitySet.new;
	}


	run {
		this.stop;
		this.startListen;
		this.initSynths;
	}

	stop {
		nodeWatcher.free;
		this.freeSynths;
	}

	appendSynth {
		var size = synths.size;
		var running = (size == 0);
		this.addSynth(running)
	}


	initGroup {
		groupID = groupID ?? { server.nextNodeID };
		server.sendMsg("/g_new", groupID, 1, 1);
	}

	initSynths {
		this.freeSynths;
		this.initGroup;
		queueSize.do { this.appendSynth };
	}

	startListen {
		nodeWatcher = OSCFunc({ |msg|
			var nodeID;
			var inGroup = msg[2];
			if(inGroup == groupID) {

				nodeID = msg[1];
				synths.remove(nodeID);
				this.appendSynth;

			};
		}, "/n_end", server.addr)
	}

	freeSynths {
		groupID !? {
			server.sendBundle(server.latency,
				['/error', -1], ["/n_free", groupID], ['/error', -2]
			);
			groupID = nil;
		};
		synths.makeEmpty;
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
			var dt = \timeScale.ir(1) * SampleDur.ir;
			array = \array.ir(0 ! numFrames);
			signal = Duty.ar(dt, 0, Dseq(array, 1), doneAction:this.doneAction);
			signal = signal.lag(dt);
			ReplaceOut.ar(out, signal)
		}).load(server);


	}

	addSynth { |running = false|
		var id = server.nextNodeID;
		var array = Array.fill(numFrames, func);
		server.sendBundle(latency,
			["/s_new", synthName, id, 1, groupID]
			++  ["array", array, "out", bus, "timeScale", timeScale].asOSCArgArray,
			[12, id, running.binaryValue] // paused
		);
		synths.add(id);
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
			var values, times, signal, dt;
			values = \values.ir(0 ! numFrames);
			times = \times.ir(1 ! numFrames).max(1);
			dt = \timeScale.ir(1) * SampleDur.ir;
			signal = Duty.ar(dt * Dseq(times, 1), 0, Dseq(values, 1), doneAction:this.doneAction);
			signal = signal.lag(dt);
			ReplaceOut.ar(out, signal)
		}).load(server);


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
			["/s_new", synthName, id, 1, groupID]
			++ ["values", values, "times", times, "out", bus, "timeScale", timeScale].asOSCArgArray,
			[12, id, running.binaryValue] // paused or not
		);
		synths.add(id);

	}

	prDelta {
		^nextDuration * sampleDur
	}
}



