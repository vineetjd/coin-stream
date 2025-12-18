import React from 'react';
import { Activity } from 'lucide-react';

const Header = ({ connected }) => {
    return (
        <header className="max-w-7xl mx-auto mb-8 flex justify-between items-center">
            <div className="flex items-center gap-3">
                <Activity className="text-emerald-500" size={32} />
                <h1 className="text-3xl font-bold bg-gradient-to-r from-emerald-400 to-cyan-400 bg-clip-text text-transparent">
                    CoinStream
                </h1>
            </div>
            <div className="flex items-center gap-2">
                <div className={`w-3 h-3 rounded-full ${connected ? 'bg-emerald-500 animate-pulse' : 'bg-red-500'}`} />
                <span className="text-slate-400 text-sm">{connected ? 'Live Feed' : 'Connecting...'}</span>
            </div>
        </header>
    );
};

export default Header;
