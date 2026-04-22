import { useState, useEffect } from 'react';
import { useParams, useLocation, useNavigate } from 'react-router-dom';
import { paymentApi } from '@/api/client';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Label } from '@/components/ui/label';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { CreditCard, Loader2, CheckCircle2, XCircle, RefreshCw, AlertCircle } from 'lucide-react';
import { toast } from 'sonner';

export default function Payment() {
  const { bookingId } = useParams();
  const location = useLocation();
  const navigate = useNavigate();
  const booking = location.state?.booking;

  const [paymentMethod, setPaymentMethod] = useState('UPI');
  const [loading, setLoading] = useState(false);
  const [payment, setPayment] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    paymentApi.getByBooking(bookingId)
      .then(({ data }) => setPayment(data))
      .catch(() => {});
  }, [bookingId]);

  const handlePay = async () => {
    setError('');
    setLoading(true);
    try {
      const { data } = await paymentApi.initiate({ bookingId: parseInt(bookingId), paymentMethod });
      setPayment(data);
      if (data.paymentStatus === 'SUCCESS') {
        toast.success('Payment successful!');
      } else {
        toast.error(`Payment ${data.paymentStatus.toLowerCase()}: ${data.failureReason || 'Unknown error'}`);
      }
    } catch (err) {
      setError(err.response?.data?.message || 'Payment failed');
    } finally {
      setLoading(false);
    }
  };

  const handleRetry = async () => {
    if (!payment?.id) return;
    setError('');
    setLoading(true);
    try {
      const { data } = await paymentApi.retry(payment.id);
      setPayment(data);
      if (data.paymentStatus === 'SUCCESS') {
        toast.success('Payment successful!');
      } else {
        toast.error('Payment failed again. You can retry.');
      }
    } catch (err) {
      setError(err.response?.data?.message || 'Retry failed');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="max-w-xl mx-auto space-y-6">
      <h1 className="text-2xl font-bold">Payment</h1>

      {booking && (
        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="text-lg">Booking Details</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-2 gap-4 text-sm">
              <div><p className="text-muted-foreground">PNR</p><p className="font-semibold font-mono">{booking.pnr}</p></div>
              <div><p className="text-muted-foreground">Status</p><Badge>{booking.bookingStatus}</Badge></div>
              <div><p className="text-muted-foreground">Passengers</p><p className="font-semibold">{booking.passengerCount}</p></div>
              <div><p className="text-muted-foreground">Total Fare</p><p className="font-semibold text-lg">₹{booking.totalFare}</p></div>
            </div>
          </CardContent>
        </Card>
      )}

      {error && (
        <Alert variant="destructive">
          <AlertCircle className="h-4 w-4" />
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      {payment?.paymentStatus === 'SUCCESS' ? (
        <Card className="border-green-200 bg-green-50">
          <CardContent className="pt-6 text-center">
            <CheckCircle2 className="h-16 w-16 text-green-600 mx-auto mb-4" />
            <h2 className="text-xl font-bold text-green-800">Payment Successful!</h2>
            <p className="text-green-700 mt-1">Transaction ID: {payment.gatewayTransactionId}</p>
            <p className="text-green-700">Amount: ₹{payment.amount}</p>
            <div className="flex gap-3 justify-center mt-6">
              <Button onClick={() => navigate('/my-bookings')}>View My Bookings</Button>
              <Button variant="outline" onClick={() => navigate(`/pnr`)}>Check PNR</Button>
            </div>
          </CardContent>
        </Card>
      ) : payment?.paymentStatus === 'FAILED' ? (
        <Card className="border-red-200 bg-red-50">
          <CardContent className="pt-6 text-center">
            <XCircle className="h-16 w-16 text-red-500 mx-auto mb-4" />
            <h2 className="text-xl font-bold text-red-800">Payment Failed</h2>
            <p className="text-red-700 mt-1">{payment.failureReason || 'Transaction could not be completed'}</p>
            <Button className="mt-6" onClick={handleRetry} disabled={loading}>
              <RefreshCw className="h-4 w-4 mr-2" />
              {loading ? 'Retrying...' : 'Retry Payment'}
            </Button>
          </CardContent>
        </Card>
      ) : (
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <CreditCard className="h-5 w-5" />
              Make Payment
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <div>
              <Label>Payment Method</Label>
              <Select value={paymentMethod} onValueChange={setPaymentMethod}>
                <SelectTrigger><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="UPI">UPI</SelectItem>
                  <SelectItem value="CREDIT_CARD">Credit Card</SelectItem>
                  <SelectItem value="DEBIT_CARD">Debit Card</SelectItem>
                  <SelectItem value="NET_BANKING">Net Banking</SelectItem>
                </SelectContent>
              </Select>
            </div>
            {booking && (
              <div className="bg-muted p-4 rounded-lg text-center">
                <p className="text-sm text-muted-foreground">Amount to pay</p>
                <p className="text-3xl font-bold">₹{booking.totalFare}</p>
              </div>
            )}
            <Button className="w-full" size="lg" onClick={handlePay} disabled={loading}>
              {loading ? <Loader2 className="h-5 w-5 animate-spin mr-2" /> : <CreditCard className="h-5 w-5 mr-2" />}
              {loading ? 'Processing...' : 'Pay Now'}
            </Button>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
