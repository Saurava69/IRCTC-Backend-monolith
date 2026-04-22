import { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { Card, CardContent } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Button } from '@/components/ui/button';
import { stationApi } from '@/api/client';
import { Search, ArrowRightLeft, Train, MapPin, Calendar } from 'lucide-react';

function StationAutocomplete({ label, value, onChange, placeholder }) {
  const [query, setQuery] = useState(value || '');
  const [suggestions, setSuggestions] = useState([]);
  const [open, setOpen] = useState(false);
  const ref = useRef(null);

  useEffect(() => {
    const handler = (e) => { if (ref.current && !ref.current.contains(e.target)) setOpen(false); };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, []);

  useEffect(() => {
    if (query.length < 2) { setSuggestions([]); return; }
    const timer = setTimeout(async () => {
      try {
        const { data } = await stationApi.search(query, 0, 8);
        setSuggestions(data.content || []);
        setOpen(true);
      } catch { setSuggestions([]); }
    }, 300);
    return () => clearTimeout(timer);
  }, [query]);

  return (
    <div className="relative" ref={ref}>
      <Label className="text-sm font-medium mb-1.5 block">{label}</Label>
      <div className="relative">
        <MapPin className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
        <Input
          className="pl-9"
          placeholder={placeholder}
          value={query}
          onChange={(e) => { setQuery(e.target.value); onChange(''); }}
          onFocus={() => suggestions.length > 0 && setOpen(true)}
        />
      </div>
      {open && suggestions.length > 0 && (
        <div className="absolute z-50 w-full mt-1 bg-card border rounded-md shadow-lg max-h-48 overflow-y-auto">
          {suggestions.map((s) => (
            <button
              key={s.id}
              type="button"
              className="w-full text-left px-3 py-2 hover:bg-muted text-sm flex justify-between items-center"
              onClick={() => { setQuery(`${s.name} (${s.code})`); onChange(s.code); setOpen(false); }}
            >
              <span className="font-medium">{s.name}</span>
              <span className="text-muted-foreground text-xs">{s.code}</span>
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

export default function Home() {
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [date, setDate] = useState(() => {
    const d = new Date();
    d.setDate(d.getDate() + 1);
    return d.toISOString().split('T')[0];
  });
  const navigate = useNavigate();

  const handleSearch = (e) => {
    e.preventDefault();
    if (from && to && date) {
      navigate(`/search?from=${from}&to=${to}&date=${date}`);
    }
  };

  const swapStations = () => {
    setFrom(to);
    setTo(from);
  };

  return (
    <div className="space-y-12">
      <section className="text-center py-12">
        <div className="flex justify-center mb-6">
          <div className="bg-primary/10 p-4 rounded-full">
            <Train className="h-12 w-12 text-primary" />
          </div>
        </div>
        <h1 className="text-4xl md:text-5xl font-bold text-foreground mb-4">
          Book Your Train Tickets
        </h1>
        <p className="text-lg text-muted-foreground max-w-2xl mx-auto">
          Search trains, check availability, and book tickets across India's railway network.
        </p>
      </section>

      <Card className="max-w-3xl mx-auto shadow-lg border-2">
        <CardContent className="pt-6">
          <form onSubmit={handleSearch} className="space-y-6">
            <div className="grid grid-cols-1 md:grid-cols-[1fr,auto,1fr] gap-4 items-end">
              <StationAutocomplete
                label="From Station"
                value={from}
                onChange={setFrom}
                placeholder="e.g. New Delhi"
              />
              <Button type="button" variant="outline" size="icon" className="mt-6 self-center" onClick={swapStations}>
                <ArrowRightLeft className="h-4 w-4" />
              </Button>
              <StationAutocomplete
                label="To Station"
                value={to}
                onChange={setTo}
                placeholder="e.g. Mumbai"
              />
            </div>

            <div className="grid grid-cols-1 md:grid-cols-[1fr,auto] gap-4 items-end">
              <div>
                <Label className="text-sm font-medium mb-1.5 block">Travel Date</Label>
                <div className="relative">
                  <Calendar className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
                  <Input
                    type="date"
                    className="pl-9"
                    value={date}
                    onChange={(e) => setDate(e.target.value)}
                    min={new Date().toISOString().split('T')[0]}
                    required
                  />
                </div>
              </div>
              <Button type="submit" size="lg" className="bg-accent text-accent-foreground hover:bg-accent/90" disabled={!from || !to}>
                <Search className="h-5 w-5 mr-2" />
                Search Trains
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>

      <section className="grid grid-cols-1 md:grid-cols-3 gap-6 max-w-4xl mx-auto">
        {[
          { icon: Search, title: 'Search & Compare', desc: 'Find trains by route and date with real-time availability' },
          { icon: Train, title: 'Instant Booking', desc: 'Book confirmed, RAC, or waitlisted tickets in seconds' },
          { icon: MapPin, title: 'PNR Tracking', desc: 'Check your booking status anytime with PNR lookup' },
        ].map(({ icon: Icon, title, desc }) => (
          <Card key={title} className="text-center p-6">
            <Icon className="h-8 w-8 text-primary mx-auto mb-3" />
            <h3 className="font-semibold mb-1">{title}</h3>
            <p className="text-sm text-muted-foreground">{desc}</p>
          </Card>
        ))}
      </section>
    </div>
  );
}
