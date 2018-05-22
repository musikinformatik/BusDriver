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
)

(
b = Bus.audio(s, 1);
d = BusDriver(f, b, 512);
d.run;
)

d.stop;

d.run


(
b = Bus.audio(s, 1);
d = SimpleBusDriver(f, b, 512);
d.run;
)

d.stop;


d.run

Done


[ "/g_freeAll", 0 ]
[ "/g_new", 0, 1, 1 ]

s.nextNodeID

