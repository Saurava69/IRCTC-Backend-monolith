import { useState, useEffect } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { trainApi } from '@/api/client';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Separator } from '@/components/ui/separator';
import { Train, Clock, MapPin, ArrowRight, Loader2 } from 'lucide-react';

const coachColors = {
  FIRST_AC: 'bg-purple-100 text-purple-800',
  SECOND_AC: 'bg-blue-100 text-blue-800',
  THIRD_AC: 'bg-cyan-100 text-cyan-800',
  SLEEPER: 'bg-green-100 text-green-800',
  GENERAL: 'bg-gray-100 text-gray-800',
};

export default function SearchResults() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const [results, setResults] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const from = searchParams.get('from');
  const to = searchParams.get('to');
  const date = searchParams.get('date');

  useEffect(() => {
    if (!from || !to || !date) return;
    setLoading(true);
    trainApi.search(from, to, date)
      .then(({ data }) => setResults(data || []))
      .catch((err) => setError(err.response?.data?.message || 'Search failed'))
      .finally(() => setLoading(false));
  }, [from, to, date]);

  const handleBook = (result, coachType, fromStationId, toStationId) => {
    navigate('/book', {
      state: {
        trainRunId: result.trainRunId,
        coachType,
        fromStationId,
        toStationId,
        trainName: result.trainName,
        trainNumber: result.trainNumber,
        departureTime: result.departureTime,
        arrivalTime: result.arrivalTime,
        date: result.runDate,
        fromStation: result.fromStation,
        toStation: result.toStation,
      },
    });
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center py-20">
        <Loader2 className="h-8 w-8 animate-spin text-primary" />
        <span className="ml-3 text-lg">Searching trains...</span>
      </div>
    );
  }

  if (error) {
    return (
      <div className="text-center py-20">
        <p className="text-destructive text-lg">{error}</p>
        <Button variant="outline" className="mt-4" onClick={() => navigate('/')}>
          Back to Search
        </Button>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-3 flex-wrap">
        <h1 className="text-2xl font-bold">
          <span>{from}</span>
          <ArrowRight className="inline h-5 w-5 mx-2" />
          <span>{to}</span>
        </h1>
        <Badge variant="secondary">{date}</Badge>
        <Badge variant="outline">{results.length} train{results.length !== 1 ? 's' : ''} found</Badge>
      </div>

      {results.length === 0 ? (
        <Card className="text-center p-12">
          <Train className="h-12 w-12 text-muted-foreground mx-auto mb-4" />
          <p className="text-lg font-medium">No trains found for this route and date</p>
          <p className="text-muted-foreground mt-1">Try different stations or dates</p>
          <Button className="mt-4" onClick={() => navigate('/')}>Search Again</Button>
        </Card>
      ) : (
        <div className="space-y-4">
          {results.map((result) => (
            <Card key={result.trainRunId} className="overflow-hidden">
              <CardHeader className="pb-3">
                <div className="flex items-center justify-between flex-wrap gap-2">
                  <CardTitle className="flex items-center gap-2 text-lg">
                    <Train className="h-5 w-5 text-primary" />
                    {result.trainName}
                    <Badge variant="outline" className="font-mono">{result.trainNumber}</Badge>
                  </CardTitle>
                  <Badge>{result.trainType}</Badge>
                </div>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="flex items-center gap-4 text-sm">
                  <div className="text-center">
                    <p className="text-2xl font-bold">{result.departureTime || '--:--'}</p>
                    <p className="text-muted-foreground flex items-center gap-1">
                      <MapPin className="h-3 w-3" />
                      {result.fromStation?.name || from}
                    </p>
                  </div>
                  <div className="flex-1 text-center">
                    <div className="border-t border-dashed border-muted-foreground/30 relative">
                      <span className="absolute -top-3 left-1/2 -translate-x-1/2 bg-card px-2 text-xs text-muted-foreground flex items-center gap-1">
                        <Clock className="h-3 w-3" />
                        {result.durationMinutes ? `${Math.floor(result.durationMinutes / 60)}h ${result.durationMinutes % 60}m` : '--'}
                      </span>
                    </div>
                  </div>
                  <div className="text-center">
                    <p className="text-2xl font-bold">{result.arrivalTime || '--:--'}</p>
                    <p className="text-muted-foreground flex items-center gap-1">
                      <MapPin className="h-3 w-3" />
                      {result.toStation?.name || to}
                    </p>
                  </div>
                </div>

                <Separator />

                <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
                  {((result.availability?.length > 0 ? result.availability : null) ||
                    (result.fares || []).map((f) => ({
                      coachType: f.coachType,
                      availableSeats: null,
                      racSeats: null,
                      waitlistCount: null,
                    }))
                  ).map((avail) => {
                    const fare = result.fares?.find((f) => f.coachType === avail.coachType);
                    const hasAvailability = avail.availableSeats !== null;
                    return (
                      <div key={avail.coachType} className={`rounded-lg p-3 ${coachColors[avail.coachType] || 'bg-muted'}`}>
                        <div className="flex justify-between items-center mb-2">
                          <span className="font-semibold text-sm">{avail.coachType.replace('_', ' ')}</span>
                          {fare && <span className="font-bold">₹{fare.baseFare}</span>}
                        </div>
                        {hasAvailability ? (
                          <div className="text-xs space-y-0.5">
                            <p>Available: <span className="font-semibold">{avail.availableSeats}</span></p>
                            <p>RAC: <span className="font-semibold">{avail.racSeats}</span></p>
                            <p>WL: <span className="font-semibold">{avail.waitlistCount}</span></p>
                          </div>
                        ) : (
                          <p className="text-xs text-muted-foreground">Check availability after booking</p>
                        )}
                        <Button
                          size="sm"
                          className="w-full mt-2"
                          variant={!hasAvailability || avail.availableSeats > 0 ? 'default' : 'secondary'}
                          onClick={() => handleBook(result, avail.coachType, result.fromStation?.id, result.toStation?.id)}
                        >
                          {!hasAvailability ? 'Book Now' : avail.availableSeats > 0 ? 'Book Now' : avail.racSeats > 0 ? 'Book RAC' : 'Join Waitlist'}
                        </Button>
                      </div>
                    );
                  })}
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}
