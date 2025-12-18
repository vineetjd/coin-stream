import { useState, useEffect, useRef } from 'react';
import SockJS from 'sockjs-client/dist/sockjs';
import { Client } from '@stomp/stompjs';
import Header from './components/Header';
import CoinTicker from './components/CoinTicker';
import PriceChart from './components/PriceChart';

function App() {

  const pricesRef = useRef({
    BTC: { current: 0, history: [], change: 0, candles: [] },
    ETH: { current: 0, history: [], change: 0, candles: [] },
    SOL: { current: 0, history: [], change: 0, candles: [] }
  });

  const [prices, setPrices] = useState(pricesRef.current);
  const [connected, setConnected] = useState(false);
  const [selectedCoin, setSelectedCoin] = useState('BTC');

  useEffect(() => {
    // websocket
    const wsUrl = import.meta.env.VITE_WS_URL + '/ws-market';
    const socket = new SockJS(wsUrl);

    const client = new Client({
      webSocketFactory: () => socket,
      onConnect: () => {
        setConnected(true);
        console.log('Connected to WS at ' + wsUrl);

        // raw prices
        client.subscribe('/topic/prices', (message) => {
          const data = JSON.parse(message.body);
          handlePriceBuf(data);
        });

        // candles + sma
        client.subscribe('/topic/analytics', (message) => {
          console.log('WS: Got Analytics Message', message.body.substring(0, 50));
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
  }, []);

  const handlePriceBuf = (data) => {
    const prev = pricesRef.current;
    const coin = prev[data.symbol] || { current: 0, history: [], change: 0, candles: [] };

    // simple change vs previous
    const change = coin.current > 0 ? ((data.price - coin.current) / coin.current) * 100 : 0;

    prev[data.symbol] = {
      ...coin,
      current: data.price,
      change: change
    };
  };

  const handleAnalyticsBuf = (data) => {
    // console.log('Processing Analytics:', data.symbol, data.windowStart);
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
  };

  return (
    <div className="min-h-screen p-8 bg-slate-950 bg-[radial-gradient(ellipse_at_top,_var(--tw-gradient-stops))] from-slate-900 via-slate-950 to-slate-950">
      <Header connected={connected} />

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
