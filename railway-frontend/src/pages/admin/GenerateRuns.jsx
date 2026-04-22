import { useState, useEffect } from 'react';
import { adminApi, trainApi } from '@/api/client';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Button } from '@/components/ui/button';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { Loader2, Play, AlertCircle, CheckCircle2 } from 'lucide-react';
import { toast } from 'sonner';

export default function GenerateRuns() {
  const [trains, setTrains] = useState([]);
  const [form, setForm] = useState({ trainId: '', fromDate: '', toDate: '' });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [result, setResult] = useState(null);

  useEffect(() => { trainApi.getAll().then(({ data }) => setTrains(data || [])); }, []);

  const handleGenerate = async (e) => {
    e.preventDefault();
    setError('');
    setResult(null);
    setLoading(true);
    try {
      const { data } = await adminApi.generateTrainRuns({
        trainId: parseInt(form.trainId),
        fromDate: form.fromDate,
        toDate: form.toDate,
      });
      setResult(data);
      toast.success('Train runs generated');
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to generate train runs');
    } finally { setLoading(false); }
  };

  return (
    <div className="max-w-xl mx-auto space-y-6">
      <h1 className="text-2xl font-bold">Generate Train Runs</h1>

      <Card>
        <CardHeader><CardTitle className="text-lg">Generate Runs for Date Range</CardTitle></CardHeader>
        <CardContent>
          <form onSubmit={handleGenerate} className="space-y-4">
            {error && <Alert variant="destructive"><AlertCircle className="h-4 w-4" /><AlertDescription>{error}</AlertDescription></Alert>}
            <div>
              <Label className="text-xs">Train</Label>
              <Select value={form.trainId} onValueChange={(v) => setForm({ ...form, trainId: v })}>
                <SelectTrigger><SelectValue placeholder="Select train" /></SelectTrigger>
                <SelectContent>{trains.map((t) => <SelectItem key={t.id} value={String(t.id)}>{t.name} ({t.trainNumber})</SelectItem>)}</SelectContent>
              </Select>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div><Label className="text-xs">From Date</Label><Input type="date" value={form.fromDate} onChange={(e) => setForm({ ...form, fromDate: e.target.value })} required /></div>
              <div><Label className="text-xs">To Date</Label><Input type="date" value={form.toDate} onChange={(e) => setForm({ ...form, toDate: e.target.value })} required /></div>
            </div>
            <Button type="submit" className="w-full" disabled={loading || !form.trainId}>
              {loading ? <Loader2 className="h-4 w-4 animate-spin mr-1" /> : <Play className="h-4 w-4 mr-1" />}
              Generate Train Runs
            </Button>
          </form>
        </CardContent>
      </Card>

      {result && (
        <Card className="border-green-200 bg-green-50">
          <CardContent className="pt-6 text-center">
            <CheckCircle2 className="h-12 w-12 text-green-600 mx-auto mb-3" />
            <p className="font-semibold text-green-800">Train Runs Generated Successfully</p>
            <pre className="text-xs text-left bg-white p-3 rounded mt-3 overflow-auto">{JSON.stringify(result, null, 2)}</pre>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
