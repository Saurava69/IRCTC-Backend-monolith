import { useState } from 'react';
import { adminApi } from '@/api/client';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { Separator } from '@/components/ui/separator';
import { Loader2, RefreshCw, Play, Database, Clock, Trash2, Search } from 'lucide-react';
import { toast } from 'sonner';

const jobs = [
  { name: 'booking-cleanup', label: 'Booking Cleanup', desc: 'Cancel unpaid bookings past timeout', icon: Trash2 },
  { name: 'train-run-generation', label: 'Train Run Generation', desc: 'Generate train runs for next 7 days', icon: Play },
  { name: 'search-reindex', label: 'Search Reindex', desc: 'Full Elasticsearch reindex', icon: Search },
  { name: 'stale-cleanup', label: 'Stale Data Cleanup', desc: 'Mark old train runs as completed', icon: Clock },
];

export default function AdminTools() {
  const [reindexing, setReindexing] = useState(false);
  const [triggeringJob, setTriggeringJob] = useState(null);
  const [results, setResults] = useState({});

  const handleReindex = async () => {
    setReindexing(true);
    try {
      const { data } = await adminApi.reindexSearch();
      setResults({ ...results, reindex: data });
      toast.success('Reindex completed');
    } catch (err) {
      toast.error(err.response?.data?.message || 'Reindex failed');
    } finally { setReindexing(false); }
  };

  const handleTrigger = async (jobName) => {
    setTriggeringJob(jobName);
    try {
      const { data } = await adminApi.triggerJob(jobName);
      setResults({ ...results, [jobName]: data });
      toast.success(`${jobName} triggered`);
    } catch (err) {
      toast.error(err.response?.data?.message || `Failed to trigger ${jobName}`);
    } finally { setTriggeringJob(null); }
  };

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold">Admin Tools</h1>

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Database className="h-5 w-5 text-primary" />
            Elasticsearch
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          <p className="text-sm text-muted-foreground">Rebuild the search index from PostgreSQL data. Safe to run anytime — it's a full reindex.</p>
          <Button onClick={handleReindex} disabled={reindexing}>
            {reindexing ? <Loader2 className="h-4 w-4 animate-spin mr-1" /> : <RefreshCw className="h-4 w-4 mr-1" />}
            {reindexing ? 'Reindexing...' : 'Reindex Now'}
          </Button>
          {results.reindex && (
            <Alert><AlertDescription><pre className="text-xs">{JSON.stringify(results.reindex, null, 2)}</pre></AlertDescription></Alert>
          )}
        </CardContent>
      </Card>

      <Separator />

      <h2 className="text-xl font-semibold">Scheduled Jobs</h2>
      <p className="text-sm text-muted-foreground">Manually trigger any scheduled job. Jobs are idempotent — safe to run multiple times.</p>

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        {jobs.map(({ name, label, desc, icon: Icon }) => (
          <Card key={name}>
            <CardContent className="pt-6">
              <div className="flex items-start gap-3">
                <Icon className="h-8 w-8 text-primary shrink-0" />
                <div className="flex-1">
                  <p className="font-semibold">{label}</p>
                  <p className="text-xs text-muted-foreground mb-3">{desc}</p>
                  <Button size="sm" onClick={() => handleTrigger(name)} disabled={triggeringJob === name}>
                    {triggeringJob === name ? <Loader2 className="h-4 w-4 animate-spin mr-1" /> : <Play className="h-4 w-4 mr-1" />}
                    Trigger
                  </Button>
                  {results[name] && (
                    <pre className="text-xs bg-muted p-2 rounded mt-2">{JSON.stringify(results[name], null, 2)}</pre>
                  )}
                </div>
              </div>
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  );
}
