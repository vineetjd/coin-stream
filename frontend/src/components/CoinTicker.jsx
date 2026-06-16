import React from 'react';
import { DollarSign, TrendingUp, TrendingDown } from 'lucide-react';

const CoinCard = ({ symbol, data, selected, onClick }) => (
    <button
        type="button"
        onClick={onClick}
        aria-pressed={selected}
        aria-label={`Select ${symbol}, current price $${data.current.toFixed(2)}`}
        className={`card w-full text-left cursor-pointer transition-all duration-300 hover:scale-105 focus:outline-none focus-visible:ring-2 focus-visible:ring-emerald-400 ${selected ? 'ring-2 ring-emerald-500 bg-slate-700' : ''}`}
    >
        <div className="flex justify-between items-center mb-2">
            <div className="flex items-center gap-2">
                <div className="p-2 bg-slate-900 rounded-lg text-emerald-400">
                    <DollarSign size={20} />
                </div>
                <span className="font-bold text-lg">{symbol}</span>
            </div>
            {data.change !== 0 && (
                <div className={`flex items-center gap-1 text-sm ${data.change >= 0 ? 'text-emerald-400' : 'text-red-400'}`}>
                    {data.change >= 0 ? <TrendingUp size={16} /> : <TrendingDown size={16} />}
                    {Math.abs(data.change).toFixed(2)}%
                </div>
            )}
        </div>
        <div className="text-2xl font-bold font-mono">
            ${data.current.toFixed(2)}
        </div>
    </button>
);

const CoinTicker = ({ prices, selectedCoin, onSelectCoin }) => {
    return (
        <div className="lg:col-span-1 space-y-4">
            {Object.entries(prices).map(([symbol, data]) => (
                <CoinCard
                    key={symbol}
                    symbol={symbol}
                    data={data}
                    selected={selectedCoin === symbol}
                    onClick={() => onSelectCoin(symbol)}
                />
            ))}
        </div>
    );
};

export default CoinTicker;
