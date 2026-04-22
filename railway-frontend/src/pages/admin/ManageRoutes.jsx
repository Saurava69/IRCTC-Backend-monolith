import { useState, useEffect } from 'react';
import { routeApi, trainApi, stationApi } from '@/api/client';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Button } from '@/components/ui/button';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { Loader2, Plus, X, AlertCircle } from 'lucide-react';
import { toast } from 'sonner';

const emptyStop = { stationId: '', sequenceNumber: 1, arrivalTime: '', departureTime: '', haltMinutes: 0, distanceFromOriginKm: 0, dayOffset: 0 };

export default function ManageRoutes() {
  const [trains, setTrains] = useState([]);
  const [stations, setStations] = useState([]);
  const [selectedTrain, setSelectedTrain] = useState('');
  const [routeName, setRouteName] = useState('');
  const [stops, setStops] = useState([{ ...emptyStop }, { ...emptyStop, sequenceNumber: 2 }]);
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState('');
  const [routes, setRoutes] = useState([]);

  useEffect(() => {
    Promise.all([trainApi.getAll(), stationApi.search('', 0, 100)])
      .then(([t, s]) => { setTrains(t.data || []); setStations(s.data.content || []); });
  }, []);

  useEffect(() => {
    if (selectedTrain) {
      routeApi.getByTrain(selectedTrain).then(({ data }) => setRoutes(data || [])).catch(() => {});
    }
  }, [selectedTrain]);

  const addStop = () => setStops([...stops, { ...emptyStop, sequenceNumber: stops.length + 1 }]);
  const removeStop = (i) => stops.length > 2 && setStops(stops.filter((_, idx) => idx !== i));
  const updateStop = (i, field, val) => { const u = [...stops]; u[i] = { ...u[i], [field]: val }; setStops(u); };

  const handleCreate = async (e) => {
    e.preventDefault();
    setError('');
    setCreating(true);
    try {
      await routeApi.create({
        trainId: parseInt(selectedTrain),
        routeName,
        stations: stops.map((s) => ({
          ...s,
          stationId: parseInt(s.stationId),
          sequenceNumber: parseInt(s.sequenceNumber),
          haltMinutes: parseInt(s.haltMinutes) || 0,
          distanceFromOriginKm: parseInt(s.distanceFromOriginKm) || 0,
          dayOffset: parseInt(s.dayOffset) || 0,
          arrivalTime: s.arrivalTime || null,
          departureTime: s.departureTime || null,
        })),
      });
      toast.success('Route created');
      setRouteName('');
      setStops([{ ...emptyStop }, { ...emptyStop, sequenceNumber: 2 }]);
      if (selectedTrain) routeApi.getByTrain(selectedTrain).then(({ data }) => setRoutes(data || []));
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to create route');
    } finally { setCreating(false); }
  };

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold">Manage Routes</h1>

      <Card>
        <CardHeader><CardTitle className="text-lg">Create Route</CardTitle></CardHeader>
        <CardContent>
          <form onSubmit={handleCreate} className="space-y-4">
            {error && <Alert variant="destructive"><AlertCircle className="h-4 w-4" /><AlertDescription>{error}</AlertDescription></Alert>}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div>
                <Label className="text-xs">Train</Label>
                <Select value={selectedTrain} onValueChange={setSelectedTrain}>
                  <SelectTrigger><SelectValue placeholder="Select train" /></SelectTrigger>
                  <SelectContent>{trains.map((t) => <SelectItem key={t.id} value={String(t.id)}>{t.name} ({t.trainNumber})</SelectItem>)}</SelectContent>
                </Select>
              </div>
              <div><Label className="text-xs">Route Name</Label><Input placeholder="Via Jaipur" value={routeName} onChange={(e) => setRouteName(e.target.value)} /></div>
            </div>

            <div className="space-y-3">
              <div className="flex items-center justify-between">
                <Label className="font-semibold">Stops ({stops.length})</Label>
                <Button type="button" variant="outline" size="sm" onClick={addStop}><Plus className="h-4 w-4 mr-1" />Add Stop</Button>
              </div>
              {stops.map((s, i) => (
                <div key={i} className="grid grid-cols-2 sm:grid-cols-4 lg:grid-cols-8 gap-2 items-end bg-muted p-3 rounded-lg">
                  <div className="col-span-2">
                    <Label className="text-xs">Station</Label>
                    <Select value={s.stationId} onValueChange={(v) => updateStop(i, 'stationId', v)}>
                      <SelectTrigger><SelectValue placeholder="Select" /></SelectTrigger>
                      <SelectContent>{stations.map((st) => <SelectItem key={st.id} value={String(st.id)}>{st.name} ({st.code})</SelectItem>)}</SelectContent>
                    </Select>
                  </div>
                  <div><Label className="text-xs">Seq</Label><Input type="number" value={s.sequenceNumber} onChange={(e) => updateStop(i, 'sequenceNumber', e.target.value)} min="1" /></div>
                  <div><Label className="text-xs">Arrival</Label><Input type="time" value={s.arrivalTime} onChange={(e) => updateStop(i, 'arrivalTime', e.target.value)} /></div>
                  <div><Label className="text-xs">Departure</Label><Input type="time" value={s.departureTime} onChange={(e) => updateStop(i, 'departureTime', e.target.value)} /></div>
                  <div><Label className="text-xs">Halt (min)</Label><Input type="number" value={s.haltMinutes} onChange={(e) => updateStop(i, 'haltMinutes', e.target.value)} min="0" /></div>
                  <div><Label className="text-xs">Distance (km)</Label><Input type="number" value={s.distanceFromOriginKm} onChange={(e) => updateStop(i, 'distanceFromOriginKm', e.target.value)} min="0" /></div>
                  <Button type="button" variant="ghost" size="icon" onClick={() => removeStop(i)} disabled={stops.length <= 2}><X className="h-4 w-4" /></Button>
                </div>
              ))}
            </div>

            <Button type="submit" disabled={creating || !selectedTrain}>{creating ? <Loader2 className="h-4 w-4 animate-spin mr-1" /> : <Plus className="h-4 w-4 mr-1" />}Create Route</Button>
          </form>
        </CardContent>
      </Card>

      {routes.length > 0 && (
        <Card>
          <CardHeader><CardTitle className="text-lg">Existing Routes</CardTitle></CardHeader>
          <CardContent className="space-y-3">
            {routes.map((r) => (
              <div key={r.id} className="bg-muted p-3 rounded-lg">
                <p className="font-semibold">{r.routeName || `Route #${r.id}`} <span className="text-xs text-muted-foreground">(Train {r.trainNumber})</span></p>
                <div className="flex flex-wrap gap-1 mt-1">
                  {r.stations?.map((s, i) => (
                    <span key={i} className="text-xs">
                      {s.station?.name || s.station?.code}{i < r.stations.length - 1 ? ' → ' : ''}
                    </span>
                  ))}
                </div>
              </div>
            ))}
          </CardContent>
        </Card>
      )}
    </div>
  );
}
