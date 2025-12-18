import React from 'react';
import { ComposedChart, Bar, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Cell, ReferenceLine, ReferenceArea } from 'recharts';

const CustomTooltip = ({ active, payload, label }) => {
    if (active && payload && payload.length) {
        const data = payload[0].payload;
        return (
            <div className="bg-slate-800 border border-slate-700 p-3 rounded-lg shadow-xl font-mono text-sm">
                <p className="text-slate-400 mb-2">{new Date(label).toLocaleTimeString()}</p>
                <div className="grid grid-cols-2 gap-x-4 gap-y-1">
                    <span className="text-slate-500">Open:</span> <span className="text-slate-200">${data.open?.toFixed(2)}</span>
                    <span className="text-slate-500">High:</span> <span className="text-slate-200">${data.high?.toFixed(2)}</span>
                    <span className="text-slate-500">Low:</span>  <span className="text-slate-200">${data.low?.toFixed(2)}</span>
                    <span className="text-slate-500">Close:</span><span className={data.close >= data.open ? "text-emerald-400" : "text-rose-400"}>${data.close?.toFixed(2)}</span>
                    <span className="text-indigo-400 mt-1">SMA:</span> <span className="text-indigo-400 mt-1">${data.sma?.toFixed(2)}</span>
                </div>
            </div>
        );
    }
    return null;
};

const PriceChart = ({ data, selectedCoin }) => {
    return (
        <div className="lg:col-span-3 card h-[600px] flex flex-col">
            <div className="mb-6 flex justify-between items-center">
                <h2 className="text-xl font-bold flex items-center gap-2">
                    {selectedCoin} Live Market
                    <span className="text-xs px-2 py-1 bg-slate-700 rounded text-slate-300">USD</span>
                </h2>
                <div className="text-sm text-slate-500">Real-time Candles (1m)</div>
            </div>

            <div className="flex justify-center overflow-x-auto">
                <ComposedChart width={800} height={400} data={data}>
                    <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" vertical={false} />
                    <XAxis
                        dataKey="time"
                        tickFormatter={(t) => new Date(t).toLocaleTimeString()}
                        stroke="#475569"
                        minTickGap={50}
                    />
                    <YAxis
                        domain={['dataMin', 'dataMax']}
                        stroke="#475569"
                        tickFormatter={(val) => `$${val}`}
                        width={80}
                        scale="linear"
                    />
                    <Tooltip content={<CustomTooltip />} cursor={{ stroke: '#475569', strokeDasharray: '5 5' }} />

                    {/* sma */}
                    <Line
                        type="monotone"
                        dataKey="sma"
                        stroke="#818cf8"
                        strokeWidth={2}
                        dot={false}
                        isAnimationActive={false}
                    />

                    {data.map((d, i) => {
                        const isUp = d.close >= d.open;
                        const color = isUp ? '#10b981' : '#ef4444';
                        const w = 20000; // 20s width
                        return (
                            <React.Fragment key={d.time}>
                                {/* Wick - Using ReferenceLine with segment */}
                                <ReferenceLine
                                    segment={[{ x: d.time, y: d.low }, { x: d.time, y: d.high }]}
                                    stroke={color}
                                    strokeWidth={2}
                                    isFront={true}
                                />
                                {/* Body - Using ReferenceArea */}
                                <ReferenceArea
                                    x1={d.time - w}
                                    x2={d.time + w}
                                    y1={d.open}
                                    y2={d.close}
                                    fill={color}
                                    stroke={color}
                                    isFront={true}
                                />
                            </React.Fragment>
                        )
                    })}
                </ComposedChart>
            </div>
        </div>
    );
};

export default PriceChart;
