import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { AuthProvider } from '@/context/AuthContext';
import { Toaster } from '@/components/ui/sonner';
import Navbar from '@/components/Navbar';
import ProtectedRoute from '@/components/ProtectedRoute';
import AdminRoute from '@/components/AdminRoute';
import Home from '@/pages/Home';
import Login from '@/pages/Login';
import Register from '@/pages/Register';
import SearchResults from '@/pages/SearchResults';
import PnrStatus from '@/pages/PnrStatus';
import BookingForm from '@/pages/BookingForm';
import Payment from '@/pages/Payment';
import MyBookings from '@/pages/MyBookings';
import AdminDashboard from '@/pages/admin/AdminDashboard';
import ManageStations from '@/pages/admin/ManageStations';
import ManageTrains from '@/pages/admin/ManageTrains';
import ManageRoutes from '@/pages/admin/ManageRoutes';
import ManageSchedules from '@/pages/admin/ManageSchedules';
import GenerateRuns from '@/pages/admin/GenerateRuns';
import AdminTools from '@/pages/admin/AdminTools';

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <div className="min-h-screen bg-background">
          <Navbar />
          <main className="max-w-7xl mx-auto px-4 py-6">
            <Routes>
              <Route path="/" element={<Home />} />
              <Route path="/login" element={<Login />} />
              <Route path="/register" element={<Register />} />
              <Route path="/search" element={<SearchResults />} />
              <Route path="/pnr" element={<PnrStatus />} />
              <Route path="/book" element={<ProtectedRoute><BookingForm /></ProtectedRoute>} />
              <Route path="/payment/:bookingId" element={<ProtectedRoute><Payment /></ProtectedRoute>} />
              <Route path="/my-bookings" element={<ProtectedRoute><MyBookings /></ProtectedRoute>} />
              <Route path="/admin" element={<AdminRoute><AdminDashboard /></AdminRoute>} />
              <Route path="/admin/stations" element={<AdminRoute><ManageStations /></AdminRoute>} />
              <Route path="/admin/trains" element={<AdminRoute><ManageTrains /></AdminRoute>} />
              <Route path="/admin/routes" element={<AdminRoute><ManageRoutes /></AdminRoute>} />
              <Route path="/admin/schedules" element={<AdminRoute><ManageSchedules /></AdminRoute>} />
              <Route path="/admin/generate-runs" element={<AdminRoute><GenerateRuns /></AdminRoute>} />
              <Route path="/admin/tools" element={<AdminRoute><AdminTools /></AdminRoute>} />
            </Routes>
          </main>
        </div>
        <Toaster richColors position="top-right" />
      </AuthProvider>
    </BrowserRouter>
  );
}
