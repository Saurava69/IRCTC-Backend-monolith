import { useState, useEffect } from 'react';
import { scheduleApi, trainApi, routeApi } from '@/api/client';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Button } from '@/components/ui/button';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Badge } from '@/components/ui/badge';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { Loader2, Plus, AlertCircle, Check } from 'lucide-react';
import { toast } from 'sonner';

const DAYS = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];

export default function ManageSchedules() {
  const [trains, setTrains] = useState([]);
  const [routes, setRoutes] = useState([]);
  const [schedules, setSchedules] = useState([]);
  const [selectedTrain, setSelectedTrain] = useState('');
  const [form, setForm] = useState({
    routeId: '', effectiveFrom: '', effectiveUntil: '',
    runsOnMonday: true, runsOnTuesday: true, runsOnWednesday: true,
    runsOnThursday: true, runsOnFriday: true, runsOnSaturday: true, runsOnSunday: true,
  });
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => { trainApi.getAll().then(({ data }) => setTrains(data || [])); }, []);

  useEffect(() => {
    if (selectedTrain) {
      routeApi.getByTrain(selectedTrain).then(({ data }) => setRoutes(data || [])).catch(() => {});
      scheduleApi.getByTrain(selectedTrain).then(({ data }) => setSchedules(data || [])).catch(() => {});
    }
  }, [selectedTrain]);

  const toggleDay = (day) => setForm({ ...form, [day]: !form[day] });

  const handleCreate = async (e) => {
    e.preventDefault();
    setError('');
    setCreating(true);
    try {
      await scheduleApi.create({
        trainId: parseInt(selectedTrain),
        routeId: parseInt(form.routeId),
        effectiveFrom: form.effectiveFrom,
        effectiveUntil: form.effectiveUntil || null,
        ...Object.fromEntries(DAYS.map((d) => [`runsOn${d}`, form[`runsOn${d}`]])),
      });
      toast.success('Schedule created');
      scheduleApi.getByTrain(selectedTrain).then(({ data }) => setSchedules(data || []));
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to create schedule');
    } finally { setCreating(false); }
  };

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold">Manage Schedules</h1>

      <Card>
        <CardHeader><CardTitle className="text-lg">Create Schedule</CardTitle></CardHeader>
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
              <div>
                <Label className="text-xs">Route</Label>
                <Select value={form.routeId} onValueChange={(v) => setForm({ ...form, routeId: v })}>
                  <SelectTrigger><SelectValue placeholder="Select route" /></SelectTrigger>
                  <SelectContent>{routes.map((r) => <SelectItem key={r.id} value={String(r.id)}>{r.routeName || `Route #${r.id}`}</SelectItem>)}</SelectContent>
                </Select>
              </div>
              <div><Label className="text-xs">Effective From</Label><Input type="date" value={form.effectiveFrom} onChange={(e) => setForm({ ...form, effectiveFrom: e.target.value })} required /></div>
              <div><Label className="text-xs">Effective Until (optional)</Label><Input type="date" value={form.effectiveUntil} onChange={(e) => setForm({ ...form, effectiveUntil: e.target.value })} /></div>
            </div>

            <div>
              <Label className="font-semibold block mb-2">Running Days</Label>
              <div className="flex flex-wrap gap-2">
                {DAYS.map((d) => {
                  const key = `runsOn${d}`;
                  return (
                    <Button key={d} type="button" variant={form[key] ? 'default' : 'outline'} size="sm" onClick={() => toggleDay(key)}>
                      {form[key] && <Check className="h-3 w-3 mr-1" />}
                      {d.slice(0, 3)}
                    </Button>
                  );
                })}
              </div>
            </div>

            <Button type="submit" disabled={creating || !selectedTrain || !form.routeId}>
              {creating ? <Loader2 className="h-4 w-4 animate-spin mr-1" /> : <Plus className="h-4 w-4 mr-1" />}Create Schedule
            </Button>
          </form>
        </CardContent>
      </Card>

      {schedules.length > 0 && (
        <Card>
          <CardHeader><CardTitle className="text-lg">Existing Schedules</CardTitle></CardHeader>
          <CardContent className="space-y-3">
            {schedules.map((s) => (
              <div key={s.id} className="bg-muted p-3 rounded-lg">
                <div className="flex items-center justify-between flex-wrap gap-2">
                  <p className="font-semibold">Schedule #{s.id} <span className="text-xs text-muted-foreground">(Train {s.trainNumber}, Route #{s.routeId})</span></p>
                  <Badge variant={s.isActive ? 'default' : 'secondary'}>{s.isActive ? 'Active' : 'Inactive'}</Badge>
                </div>
                <div className="flex gap-1 mt-1">
                  {DAYS.map((d) => (
                    <Badge key={d} variant={s[`runsOn${d}`] ? 'default' : 'outline'} className="text-xs">{d.slice(0, 2)}</Badge>
                  ))}
                </div>
                <p className="text-xs text-muted-foreground mt-1">From {s.effectiveFrom}{s.effectiveUntil ? ` to ${s.effectiveUntil}` : ' (no end)'}</p>
              </div>
            ))}
          </CardContent>
        </Card>
      )}
    </div>
  );
}
