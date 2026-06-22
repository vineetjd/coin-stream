import { useState, useEffect, useRef, useCallback } from 'react';
import SockJS from 'sockjs-client/dist/sockjs';
import { Client } from '@stomp/stompjs';
import Header from './components/Header';
import CoinTicker from './components/CoinTicker';
import PriceChart from './components/PriceChart';

const createInitialPrices = () => ({
  BTC: { current: 0, history: [], change: 0, candles: [] },
  ETH: { current: 0, history: [], change: 0, candles: [] },
  SOL: { current: 0, history: [], change: 0, candles: [] }
});

function App() {

  const pricesRef = useRef(createInitialPrices());

  // Lazy initializer (factory, not a ref read) so we don't access refs during render.
  const [prices, setPrices] = useState(createInitialPrices);
  const [connected, setConnected] = useState(false);
  const [selectedCoin, setSelectedCoin] = useState('BTC');

  // Stable callbacks (refs only) so the WebSocket effect runs once, and they are
  // declared before the effect that references them.
  const handlePriceBuf = useCallback((data) => {
    const prev = pricesRef.current;
    const coin = prev[data.symbol] || { current: 0, history: [], change: 0, candles: [] };

    // simple change vs previous
    const change = coin.current > 0 ? ((data.price - coin.current) / coin.current) * 100 : 0;

    prev[data.symbol] = {
      ...coin,
      current: data.price,
      change: change
    };
  }, []);

  const handleAnalyticsBuf = useCallback((data) => {
    const prev = pricesRef.current;
    const coin = prev[data.symbol] || { current: 0, history: [], change: 0, candles: [] };

    const newCandles = [...coin.candles];
    const lastCandle = newCandles[newCandles.length - 1];

    // update existing candle if same window start
    if (lastCandle && lastCandle.time === data.windowStart) {
      // existing candle
      newCandles[newCandles.length - 1] = {
        time: data.windowStart,
        open: data.open,
        high: data.high,
        low: data.low,
        close: data.close,
        sma: data.averagePrice
      };
    } else {
      // new candle
      newCandles.push({
        time: data.windowStart,
        open: data.open,
        high: data.high,
        low: data.low,
        close: data.close,
        sma: data.averagePrice
      });
    }

    // 50 candle limit
    if (newCandles.length > 50) newCandles.shift();

    prev[data.symbol] = {
      ...coin,
      candles: newCandles
    };
  }, []);

  useEffect(() => {
    // Same-origin endpoint: nginx proxies /ws-market to the gateway, so a
    // fresh clone works with zero configuration (no VITE_WS_URL to set).
    const socket = new SockJS('/ws-market');

    const client = new Client({
      webSocketFactory: () => socket,
      onConnect: () => {
        setConnected(true);

        // raw prices
        client.subscribe('/topic/prices', (message) => {
          const data = JSON.parse(message.body);
          handlePriceBuf(data);
        });

        // candles + sma
        client.subscribe('/topic/analytics', (message) => {
          const data = JSON.parse(message.body);
          handleAnalyticsBuf(data);
        });
      },
      onDisconnect: () => setConnected(false),
    });

    client.activate();

    // sync every 200ms
    const intervalId = setInterval(() => {
      // re-render
      setPrices({ ...pricesRef.current });
    }, 200);

    return () => {
      client.deactivate();
      clearInterval(intervalId);
    };
  }, [handlePriceBuf, handleAnalyticsBuf]);

  return (
    <div className="min-h-screen p-8 bg-slate-950 bg-[radial-gradient(ellipse_at_top,_var(--tw-gradient-stops))] from-slate-900 via-slate-950 to-slate-950">
      <Header connected={connected} />

      {!connected && (
        <div
          role="status"
          className="max-w-7xl mx-auto mb-6 rounded-lg border border-amber-500/40 bg-amber-500/10 px-4 py-3 text-sm text-amber-300"
        >
          Connecting to the live feed… retrying automatically.
        </div>
      )}

      <main className="max-w-7xl mx-auto grid grid-cols-1 lg:grid-cols-4 gap-6">
        <CoinTicker
          prices={prices}
          selectedCoin={selectedCoin}
          onSelectCoin={setSelectedCoin}
        />

        <PriceChart
          data={prices[selectedCoin].candles}
          selectedCoin={selectedCoin}
        />
      </main>
    </div>
  );
}

export default App;
