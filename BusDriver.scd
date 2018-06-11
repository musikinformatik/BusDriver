

(
f = value {
	var dt = 1/s.sampleRate;
	var c = 0;
	var freq = 64.midicps;
	var r = 2pi * freq * dt;
	{
		var z = sin(c * r) * 0.1;
		c = c + 1;
		z
	}
};

d = BusDriver(f, numFrames: 512);
//d.timeScale = 30;
//d = EventBusDriver({ (value: f.value, dur: 1) }, numFrames: 512);
d.run;
)

d.synths

x = d.synths.maxItem;
s.sendMsg("/n_run", d.synths.minItem, 1);
d.synths.minItem;
x

d.synths.makeEmpty

s.freeAll;

d.stop;

(
d = EventBusDriver({ (value: f.value, dur: 1) }, numFrames: 512);
d.run;
)

d.stop;

d.run

d.synths;

s.sendMsg("/n_run", 1019, 1);