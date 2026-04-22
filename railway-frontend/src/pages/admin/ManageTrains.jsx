import { useState, useEffect } from 'react';
import { trainApi, stationApi } from '@/api/client';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Button } from '@/components/ui/button';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Badge } from '@/components/ui/badge';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { Loader2, Plus, X, AlertCircle, Train } from 'lucide-react';
import { toast } from 'sonner';

const COACH_TYPES = ['FIRST_AC', 'SECOND_AC', 'THIRD_AC', 'SLEEPER', 'GENERAL'];
const emptyCoach = { coachNumber: '', coachType: 'SLEEPER', totalSeats: 72, totalBerths: 72, sequenceInTrain: 1 };

export default function ManageTrains() {
  const [trains, setTrains] = useState([]);
  const [stations, setStations] = useState([]);
  const [loading, setLoading] = useState(true);
  const [form, setForm] = useState({ trainNumber: '', name: '', trainType: 'EXPRESS', sourceStationId: '', destStationId: '' });
  const [coaches, setCoaches] = useState([{ ...emptyCoach }]);
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    Promise.all([trainApi.getAll(), stationApi.search('', 0, 100)])
      .then(([t, s]) => { setTrains(t.data || []); setStations(s.data.content || []); })
      .finally(() => setLoading(false));
  }, []);

  const addCoach = () => setCoaches([...coaches, { ...emptyCoach, sequenceInTrain: coaches.length + 1 }]);
  const removeCoach = (i) => coaches.length > 1 && setCoaches(coaches.filter((_, idx) => idx !== i));
  const updateCoach = (i, field, val) => {
    const u = [...coaches]; u[i] = { ...u[i], [field]: val }; setCoaches(u);
  };

  const handleCreate = async (e) => {
    e.preventDefault();
    setError('');
    setCreating(true);
    try {
      await trainApi.create({
        ...form,
        sourceStationId: parseInt(form.sourceStationId),
        destStationId: parseInt(form.destStationId),
        coaches: coaches.map((c) => ({ ...c, totalSeats: parseInt(c.totalSeats), totalBerths: parseInt(c.totalBerths), sequenceInTrain: parseInt(c.sequenceInTrain) })),
      });
      toast.success(`Train ${form.trainNumber} created`);
      setForm({ trainNumber: '', name: '', trainType: 'EXPRESS', sourceStationId: '', destStationId: '' });
      setCoaches([{ ...emptyCoach }]);
      const { data } = await trainApi.getAll();
      setTrains(data || []);
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to create train');
    } finally { setCreating(false); }
  };

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold">Manage Trains</h1>

      <Card>
        <CardHeader><CardTitle className="text-lg">Create Train</CardTitle></CardHeader>
        <CardContent>
          <form onSubmit={handleCreate} className="space-y-4">
            {error && <Alert variant="destructive"><AlertCircle className="h-4 w-4" /><AlertDescription>{error}</AlertDescription></Alert>}
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
              <div><Label className="text-xs">Train Number</Label><Input placeholder="12951" value={form.trainNumber} onChange={(e) => setForm({ ...form, trainNumber: e.target.value })} required /></div>
              <div><Label className="text-xs">Name</Label><Input placeholder="Rajdhani Express" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} required /></div>
              <div>
                <Label className="text-xs">Type</Label>
                <Select value={form.trainType} onValueChange={(v) => setForm({ ...form, trainType: v })}>
                  <SelectTrigger><SelectValue /></SelectTrigger>
                  <SelectContent>
                    {['EXPRESS', 'SUPERFAST', 'RAJDHANI', 'SHATABDI', 'DURONTO', 'MAIL', 'LOCAL'].map((t) => (
                      <SelectItem key={t} value={t}>{t}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div>
                <Label className="text-xs">Source Station</Label>
                <Select value={form.sourceStationId} onValueChange={(v) => setForm({ ...form, sourceStationId: v })}>
                  <SelectTrigger><SelectValue placeholder="Select" /></SelectTrigger>
                  <SelectContent>{stations.map((s) => <SelectItem key={s.id} value={String(s.id)}>{s.name} ({s.code})</SelectItem>)}</SelectContent>
                </Select>
              </div>
              <div>
                <Label className="text-xs">Destination Station</Label>
                <Select value={form.destStationId} onValueChange={(v) => setForm({ ...form, destStationId: v })}>
                  <SelectTrigger><SelectValue placeholder="Select" /></SelectTrigger>
                  <SelectContent>{stations.map((s) => <SelectItem key={s.id} value={String(s.id)}>{s.name} ({s.code})</SelectItem>)}</SelectContent>
                </Select>
              </div>
            </div>

            <div className="space-y-3">
              <div className="flex items-center justify-between">
                <Label className="font-semibold">Coaches ({coaches.length})</Label>
                <Button type="button" variant="outline" size="sm" onClick={addCoach}><Plus className="h-4 w-4 mr-1" />Add Coach</Button>
              </div>
              {coaches.map((c, i) => (
                <div key={i} className="grid grid-cols-2 sm:grid-cols-6 gap-2 items-end bg-muted p-3 rounded-lg">
                  <div><Label className="text-xs">Number</Label><Input value={c.coachNumber} onChange={(e) => updateCoach(i, 'coachNumber', e.target.value)} placeholder="S1" required /></div>
                  <div>
                    <Label className="text-xs">Type</Label>
                    <Select value={c.coachType} onValueChange={(v) => updateCoach(i, 'coachType', v)}>
                      <SelectTrigger><SelectValue /></SelectTrigger>
                      <SelectContent>{COACH_TYPES.map((t) => <SelectItem key={t} value={t}>{t}</SelectItem>)}</SelectContent>
                    </Select>
                  </div>
                  <div><Label className="text-xs">Seats</Label><Input type="number" value={c.totalSeats} onChange={(e) => updateCoach(i, 'totalSeats', e.target.value)} min="1" required /></div>
                  <div><Label className="text-xs">Berths</Label><Input type="number" value={c.totalBerths} onChange={(e) => updateCoach(i, 'totalBerths', e.target.value)} min="1" required /></div>
                  <div><Label className="text-xs">Sequence</Label><Input type="number" value={c.sequenceInTrain} onChange={(e) => updateCoach(i, 'sequenceInTrain', e.target.value)} min="1" required /></div>
                  <Button type="button" variant="ghost" size="icon" onClick={() => removeCoach(i)} disabled={coaches.length <= 1}><X className="h-4 w-4" /></Button>
                </div>
              ))}
            </div>

            <Button type="submit" disabled={creating}>{creating ? <Loader2 className="h-4 w-4 animate-spin mr-1" /> : <Plus className="h-4 w-4 mr-1" />}Create Train</Button>
          </form>
        </CardContent>
      </Card>

      <Card>
        <CardHeader><CardTitle className="text-lg">Trains ({trains.length})</CardTitle></CardHeader>
        <CardContent>
          {loading ? <Loader2 className="h-6 w-6 animate-spin mx-auto" /> : (
            <div className="space-y-3">
              {trains.map((t) => (
                <div key={t.id} className="flex items-center justify-between bg-muted p-3 rounded-lg">
                  <div className="flex items-center gap-3">
                    <Train className="h-5 w-5 text-primary" />
                    <div>
                      <p className="font-semibold">{t.name} <span className="font-mono text-sm text-muted-foreground">({t.trainNumber})</span></p>
                      <p className="text-xs text-muted-foreground">{t.sourceStation?.name} → {t.destStation?.name}</p>
                    </div>
                  </div>
                  <div className="flex gap-1">{t.coaches?.map((c) => <Badge key={c.id} variant="outline" className="text-xs">{c.coachNumber}</Badge>)}</div>
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
