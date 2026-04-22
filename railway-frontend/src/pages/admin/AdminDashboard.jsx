import { Link } from 'react-router-dom';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { MapPin, Train, Route, Calendar, Play, Wrench } from 'lucide-react';

const adminPages = [
  { to: '/admin/stations', icon: MapPin, title: 'Manage Stations', desc: 'Create and browse railway stations' },
  { to: '/admin/trains', icon: Train, title: 'Manage Trains', desc: 'Create trains with coach configuration' },
  { to: '/admin/routes', icon: Route, title: 'Manage Routes', desc: 'Define routes with station stops' },
  { to: '/admin/schedules', icon: Calendar, title: 'Manage Schedules', desc: 'Set train schedules and running days' },
  { to: '/admin/generate-runs', icon: Play, title: 'Generate Runs', desc: 'Generate train runs for date ranges' },
  { to: '/admin/tools', icon: Wrench, title: 'Admin Tools', desc: 'Reindex search, trigger scheduled jobs' },
];

export default function AdminDashboard() {
  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold">Admin Dashboard</h1>
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
        {adminPages.map(({ to, icon: Icon, title, desc }) => (
          <Link key={to} to={to} className="no-underline">
            <Card className="hover:shadow-md transition-shadow cursor-pointer h-full">
              <CardHeader className="pb-2">
                <CardTitle className="flex items-center gap-2 text-lg">
                  <Icon className="h-5 w-5 text-primary" />
                  {title}
                </CardTitle>
              </CardHeader>
              <CardContent>
                <p className="text-sm text-muted-foreground">{desc}</p>
              </CardContent>
            </Card>
          </Link>
        ))}
      </div>
    </div>
  );
}
