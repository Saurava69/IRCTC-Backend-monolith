import { useState } from 'react';
import { pnrApi } from '@/api/client';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { Search, Loader2, AlertCircle, FileText } from 'lucide-react';

const statusColors = {
  CONFIRMED: 'bg-green-100 text-green-800',
  PAYMENT_PENDING: 'bg-yellow-100 text-yellow-800',
  RAC: 'bg-blue-100 text-blue-800',
  WAITLISTED: 'bg-orange-100 text-orange-800',
  CANCELLED: 'bg-red-100 text-red-800',
  FAILED: 'bg-gray-100 text-gray-800',
};

export default function PnrStatus() {
  const [pnr, setPnr] = useState('');
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const handleCheck = async (e) => {
    e.preventDefault();
    if (!pnr.trim()) return;
    setError('');
    setResult(null);
    setLoading(true);
    try {
      const { data } = await pnrApi.check(pnr.trim());
      setResult(data);
    } catch (err) {
      setError(err.response?.data?.message || 'PNR not found');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="max-w-3xl mx-auto space-y-6">
      <div className="text-center">
        <h1 className="text-3xl font-bold mb-2">PNR Status Check</h1>
        <p className="text-muted-foreground">Enter your PNR number to check booking status</p>
      </div>

      <Card>
        <CardContent className="pt-6">
          <form onSubmit={handleCheck} className="flex gap-3">
            <div className="relative flex-1">
              <FileText className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
              <Input
                className="pl-9"
                placeholder="e.g. PNR2026042200001"
                value={pnr}
                onChange={(e) => setPnr(e.target.value)}
                required
              />
            </div>
            <Button type="submit" disabled={loading}>
              {loading ? <Loader2 className="h-4 w-4 animate-spin" /> : <Search className="h-4 w-4 mr-1" />}
              Check
            </Button>
          </form>
        </CardContent>
      </Card>

      {error && (
        <Alert variant="destructive">
          <AlertCircle className="h-4 w-4" />
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      {result && (
        <Card>
          <CardHeader>
            <div className="flex items-center justify-between flex-wrap gap-2">
              <CardTitle className="text-lg">PNR: {result.pnr}</CardTitle>
              <Badge className={statusColors[result.bookingStatus] || ''}>{result.bookingStatus}</Badge>
            </div>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="grid grid-cols-2 md:grid-cols-4 gap-4 text-sm">
              <div>
                <p className="text-muted-foreground">Train Run ID</p>
                <p className="font-semibold">{result.trainRunId}</p>
              </div>
              <div>
                <p className="text-muted-foreground">Coach Type</p>
                <p className="font-semibold">{result.coachType}</p>
              </div>
              <div>
                <p className="text-muted-foreground">From Station</p>
                <p className="font-semibold">{result.fromStationId}</p>
              </div>
              <div>
                <p className="text-muted-foreground">To Station</p>
                <p className="font-semibold">{result.toStationId}</p>
              </div>
            </div>

            {result.passengers?.length > 0 && (
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Name</TableHead>
                    <TableHead>Age</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead>Seat</TableHead>
                    <TableHead>Coach</TableHead>
                    <TableHead>WL/RAC</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {result.passengers.map((p, i) => (
                    <TableRow key={i}>
                      <TableCell className="font-medium">{p.name}</TableCell>
                      <TableCell>{p.age}</TableCell>
                      <TableCell>
                        <Badge variant="outline" className={statusColors[p.status] || ''}>{p.status}</Badge>
                      </TableCell>
                      <TableCell>{p.seatNumber || '-'}</TableCell>
                      <TableCell>{p.coachNumber || '-'}</TableCell>
                      <TableCell>
                        {p.waitlistNumber ? `WL ${p.waitlistNumber}` : p.racNumber ? `RAC ${p.racNumber}` : '-'}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </CardContent>
        </Card>
      )}
    </div>
  );
}
