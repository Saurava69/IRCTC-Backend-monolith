import { useState, useEffect } from 'react';
import { stationApi } from '@/api/client';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Button } from '@/components/ui/button';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { Loader2, Plus, AlertCircle } from 'lucide-react';
import { toast } from 'sonner';

export default function ManageStations() {
  const [stations, setStations] = useState([]);
  const [loading, setLoading] = useState(true);
  const [form, setForm] = useState({ code: '', name: '', city: '', state: '', zone: '' });
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState('');

  const fetchStations = async () => {
    try {
      const { data } = await stationApi.search('', 0, 50);
      setStations(data.content || []);
    } catch {} finally { setLoading(false); }
  };

  useEffect(() => { fetchStations(); }, []);

  const update = (f) => (e) => setForm({ ...form, [f]: e.target.value });

  const handleCreate = async (e) => {
    e.preventDefault();
    setError('');
    setCreating(true);
    try {
      await stationApi.create(form);
      toast.success(`Station ${form.code} created`);
      setForm({ code: '', name: '', city: '', state: '', zone: '' });
      fetchStations();
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to create station');
    } finally { setCreating(false); }
  };

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold">Manage Stations</h1>

      <Card>
        <CardHeader><CardTitle className="text-lg">Create Station</CardTitle></CardHeader>
        <CardContent>
          <form onSubmit={handleCreate} className="space-y-4">
            {error && <Alert variant="destructive"><AlertCircle className="h-4 w-4" /><AlertDescription>{error}</AlertDescription></Alert>}
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-3">
              <div><Label className="text-xs">Code</Label><Input placeholder="NDLS" value={form.code} onChange={update('code')} required maxLength={10} /></div>
              <div><Label className="text-xs">Name</Label><Input placeholder="New Delhi" value={form.name} onChange={update('name')} required /></div>
              <div><Label className="text-xs">City</Label><Input placeholder="Delhi" value={form.city} onChange={update('city')} /></div>
              <div><Label className="text-xs">State</Label><Input placeholder="Delhi" value={form.state} onChange={update('state')} /></div>
              <div><Label className="text-xs">Zone</Label><Input placeholder="NR" value={form.zone} onChange={update('zone')} /></div>
            </div>
            <Button type="submit" disabled={creating}>{creating ? <Loader2 className="h-4 w-4 animate-spin mr-1" /> : <Plus className="h-4 w-4 mr-1" />}Create Station</Button>
          </form>
        </CardContent>
      </Card>

      <Card>
        <CardHeader><CardTitle className="text-lg">Stations ({stations.length})</CardTitle></CardHeader>
        <CardContent>
          {loading ? <Loader2 className="h-6 w-6 animate-spin mx-auto" /> : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Code</TableHead><TableHead>Name</TableHead><TableHead>City</TableHead><TableHead>State</TableHead><TableHead>Zone</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {stations.map((s) => (
                  <TableRow key={s.id}>
                    <TableCell className="font-mono font-semibold">{s.code}</TableCell>
                    <TableCell>{s.name}</TableCell>
                    <TableCell>{s.city || '-'}</TableCell>
                    <TableCell>{s.state || '-'}</TableCell>
                    <TableCell>{s.zone || '-'}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
