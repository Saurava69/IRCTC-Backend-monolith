import { useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { bookingApi } from '@/api/client';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { Separator } from '@/components/ui/separator';
import { UserPlus, X, Loader2, AlertCircle, Train } from 'lucide-react';
import { toast } from 'sonner';

const emptyPassenger = { name: '', age: '', gender: 'MALE', berthPreference: '' };

export default function BookingForm() {
  const location = useLocation();
  const navigate = useNavigate();
  const state = location.state;

  const [passengers, setPassengers] = useState([{ ...emptyPassenger }]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  if (!state) {
    return (
      <div className="text-center py-20">
        <p className="text-lg">No train selected. Please search for trains first.</p>
        <Button className="mt-4" onClick={() => navigate('/')}>Search Trains</Button>
      </div>
    );
  }

  const addPassenger = () => {
    if (passengers.length < 6) setPassengers([...passengers, { ...emptyPassenger }]);
  };

  const removePassenger = (index) => {
    if (passengers.length > 1) setPassengers(passengers.filter((_, i) => i !== index));
  };

  const updatePassenger = (index, field, value) => {
    const updated = [...passengers];
    updated[index] = { ...updated[index], [field]: value };
    setPassengers(updated);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      const { data } = await bookingApi.create({
        trainRunId: state.trainRunId,
        coachType: state.coachType,
        fromStationId: state.fromStationId,
        toStationId: state.toStationId,
        passengers: passengers.map((p) => ({ ...p, age: parseInt(p.age) })),
      });
      toast.success(`Booking created! PNR: ${data.pnr}`);
      navigate(`/payment/${data.id}`, { state: { booking: data } });
    } catch (err) {
      setError(err.response?.data?.message || 'Booking failed');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="max-w-3xl mx-auto space-y-6">
      <h1 className="text-2xl font-bold">Book Tickets</h1>

      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="flex items-center gap-2 text-lg">
            <Train className="h-5 w-5 text-primary" />
            {state.trainName}
            <Badge variant="outline" className="font-mono">{state.trainNumber}</Badge>
          </CardTitle>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4 text-sm">
            <div>
              <p className="text-muted-foreground">From</p>
              <p className="font-semibold">{state.fromStation?.name || 'Station'}</p>
              <p className="text-muted-foreground">{state.departureTime}</p>
            </div>
            <div>
              <p className="text-muted-foreground">To</p>
              <p className="font-semibold">{state.toStation?.name || 'Station'}</p>
              <p className="text-muted-foreground">{state.arrivalTime}</p>
            </div>
            <div>
              <p className="text-muted-foreground">Date</p>
              <p className="font-semibold">{state.date}</p>
            </div>
            <div>
              <p className="text-muted-foreground">Coach</p>
              <Badge>{state.coachType?.replace('_', ' ')}</Badge>
            </div>
          </div>
        </CardContent>
      </Card>

      <form onSubmit={handleSubmit} className="space-y-4">
        {error && (
          <Alert variant="destructive">
            <AlertCircle className="h-4 w-4" />
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        )}

        <div className="flex items-center justify-between">
          <h2 className="text-lg font-semibold">Passengers ({passengers.length}/6)</h2>
          <Button type="button" variant="outline" size="sm" onClick={addPassenger} disabled={passengers.length >= 6}>
            <UserPlus className="h-4 w-4 mr-1" /> Add
          </Button>
        </div>

        {passengers.map((p, i) => (
          <Card key={i}>
            <CardContent className="pt-4">
              <div className="flex items-center justify-between mb-3">
                <span className="font-medium text-sm">Passenger {i + 1}</span>
                {passengers.length > 1 && (
                  <Button type="button" variant="ghost" size="icon" className="h-7 w-7" onClick={() => removePassenger(i)}>
                    <X className="h-4 w-4" />
                  </Button>
                )}
              </div>
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
                <div>
                  <Label className="text-xs">Name</Label>
                  <Input placeholder="Full name" value={p.name} onChange={(e) => updatePassenger(i, 'name', e.target.value)} required />
                </div>
                <div>
                  <Label className="text-xs">Age</Label>
                  <Input type="number" min="1" max="120" placeholder="Age" value={p.age} onChange={(e) => updatePassenger(i, 'age', e.target.value)} required />
                </div>
                <div>
                  <Label className="text-xs">Gender</Label>
                  <Select value={p.gender} onValueChange={(v) => updatePassenger(i, 'gender', v)}>
                    <SelectTrigger><SelectValue /></SelectTrigger>
                    <SelectContent>
                      <SelectItem value="MALE">Male</SelectItem>
                      <SelectItem value="FEMALE">Female</SelectItem>
                      <SelectItem value="OTHER">Other</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
                <div>
                  <Label className="text-xs">Berth Preference</Label>
                  <Select value={p.berthPreference || 'NONE'} onValueChange={(v) => updatePassenger(i, 'berthPreference', v === 'NONE' ? '' : v)}>
                    <SelectTrigger><SelectValue /></SelectTrigger>
                    <SelectContent>
                      <SelectItem value="NONE">No preference</SelectItem>
                      <SelectItem value="LOWER">Lower</SelectItem>
                      <SelectItem value="MIDDLE">Middle</SelectItem>
                      <SelectItem value="UPPER">Upper</SelectItem>
                      <SelectItem value="SIDE_LOWER">Side Lower</SelectItem>
                      <SelectItem value="SIDE_UPPER">Side Upper</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
              </div>
            </CardContent>
          </Card>
        ))}

        <Separator />

        <Button type="submit" className="w-full" size="lg" disabled={loading}>
          {loading ? <Loader2 className="h-5 w-5 animate-spin mr-2" /> : null}
          {loading ? 'Booking...' : 'Confirm Booking'}
        </Button>
      </form>
    </div>
  );
}
