import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { bookingApi } from '@/api/client';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Loader2, Train, XCircle, CreditCard } from 'lucide-react';
import { toast } from 'sonner';

const statusColors = {
  CONFIRMED: 'bg-green-100 text-green-800 border-green-300',
  PAYMENT_PENDING: 'bg-yellow-100 text-yellow-800 border-yellow-300',
  RAC: 'bg-blue-100 text-blue-800 border-blue-300',
  WAITLISTED: 'bg-orange-100 text-orange-800 border-orange-300',
  CANCELLED: 'bg-red-100 text-red-800 border-red-300',
  FAILED: 'bg-gray-100 text-gray-800 border-gray-300',
};

export default function MyBookings() {
  const navigate = useNavigate();
  const [bookings, setBookings] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [cancelPnr, setCancelPnr] = useState(null);
  const [cancelReason, setCancelReason] = useState('');
  const [cancelling, setCancelling] = useState(false);

  const fetchBookings = async (p = 0) => {
    setLoading(true);
    try {
      const { data } = await bookingApi.getMy(p);
      setBookings(data.content || []);
      setTotalPages(data.totalPages || 0);
      setPage(p);
    } catch {
      toast.error('Failed to load bookings');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchBookings(); }, []);

  const handleCancel = async () => {
    if (!cancelPnr) return;
    setCancelling(true);
    try {
      const { data } = await bookingApi.cancel(cancelPnr, cancelReason);
      toast.success(`Booking cancelled. Refund: ₹${data.refundAmount}`);
      setCancelPnr(null);
      setCancelReason('');
      fetchBookings(page);
    } catch (err) {
      toast.error(err.response?.data?.message || 'Cancellation failed');
    } finally {
      setCancelling(false);
    }
  };

  if (loading && bookings.length === 0) {
    return (
      <div className="flex items-center justify-center py-20">
        <Loader2 className="h-8 w-8 animate-spin text-primary" />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold">My Bookings</h1>

      {bookings.length === 0 ? (
        <Card className="text-center p-12">
          <Train className="h-12 w-12 text-muted-foreground mx-auto mb-4" />
          <p className="text-lg font-medium">No bookings yet</p>
          <Button className="mt-4" onClick={() => navigate('/')}>Book a Train</Button>
        </Card>
      ) : (
        <div className="space-y-4">
          {bookings.map((b) => (
            <Card key={b.id} className="overflow-hidden">
              <CardHeader className="pb-3">
                <div className="flex items-center justify-between flex-wrap gap-2">
                  <CardTitle className="text-base font-mono">{b.pnr}</CardTitle>
                  <Badge className={statusColors[b.bookingStatus] || ''}>{b.bookingStatus}</Badge>
                </div>
              </CardHeader>
              <CardContent>
                <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 text-sm mb-4">
                  <div>
                    <p className="text-muted-foreground">Coach</p>
                    <p className="font-semibold">{b.coachType?.replace('_', ' ')}</p>
                  </div>
                  <div>
                    <p className="text-muted-foreground">Passengers</p>
                    <p className="font-semibold">{b.passengerCount}</p>
                  </div>
                  <div>
                    <p className="text-muted-foreground">Fare</p>
                    <p className="font-semibold">₹{b.totalFare}</p>
                  </div>
                  <div>
                    <p className="text-muted-foreground">Booked</p>
                    <p className="font-semibold">{b.createdAt ? new Date(b.createdAt).toLocaleDateString() : '-'}</p>
                  </div>
                </div>

                {b.passengers?.length > 0 && (
                  <div className="text-xs space-y-1 mb-4">
                    {b.passengers.map((p, i) => (
                      <div key={i} className="flex items-center gap-2 bg-muted px-2 py-1 rounded">
                        <span className="font-medium">{p.name}</span>
                        <span className="text-muted-foreground">Age {p.age}</span>
                        <Badge variant="outline" className="text-xs">{p.status}</Badge>
                        {p.seatNumber && <span>Seat {p.seatNumber}</span>}
                        {p.coachNumber && <span>Coach {p.coachNumber}</span>}
                      </div>
                    ))}
                  </div>
                )}

                <div className="flex gap-2">
                  {b.bookingStatus === 'PAYMENT_PENDING' && (
                    <Button size="sm" onClick={() => navigate(`/payment/${b.id}`, { state: { booking: b } })}>
                      <CreditCard className="h-4 w-4 mr-1" /> Pay Now
                    </Button>
                  )}
                  {['CONFIRMED', 'RAC', 'WAITLISTED', 'PAYMENT_PENDING'].includes(b.bookingStatus) && (
                    <Dialog open={cancelPnr === b.pnr} onOpenChange={(open) => { if (!open) setCancelPnr(null); }}>
                      <DialogTrigger asChild>
                        <Button size="sm" variant="destructive" onClick={() => setCancelPnr(b.pnr)}>
                          <XCircle className="h-4 w-4 mr-1" /> Cancel
                        </Button>
                      </DialogTrigger>
                      <DialogContent>
                        <DialogHeader>
                          <DialogTitle>Cancel Booking</DialogTitle>
                          <DialogDescription>
                            Are you sure you want to cancel booking {b.pnr}? A refund will be initiated.
                          </DialogDescription>
                        </DialogHeader>
                        <div>
                          <Label>Reason (optional)</Label>
                          <Input value={cancelReason} onChange={(e) => setCancelReason(e.target.value)} placeholder="Reason for cancellation" />
                        </div>
                        <DialogFooter>
                          <Button variant="outline" onClick={() => setCancelPnr(null)}>Keep Booking</Button>
                          <Button variant="destructive" onClick={handleCancel} disabled={cancelling}>
                            {cancelling ? 'Cancelling...' : 'Confirm Cancel'}
                          </Button>
                        </DialogFooter>
                      </DialogContent>
                    </Dialog>
                  )}
                </div>
              </CardContent>
            </Card>
          ))}

          {totalPages > 1 && (
            <div className="flex justify-center gap-2">
              <Button variant="outline" size="sm" disabled={page === 0} onClick={() => fetchBookings(page - 1)}>Previous</Button>
              <span className="flex items-center text-sm text-muted-foreground">Page {page + 1} of {totalPages}</span>
              <Button variant="outline" size="sm" disabled={page >= totalPages - 1} onClick={() => fetchBookings(page + 1)}>Next</Button>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
