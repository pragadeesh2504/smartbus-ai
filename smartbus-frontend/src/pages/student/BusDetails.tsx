import React, { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import axios from 'axios';
import {
  ChevronLeft,
  Bus as BusIcon,
  User as UserIcon,
  Star,
  MapPin,
  Info
} from 'lucide-react';

interface BusDetailsData {
  busId: string;
  busNumber: string;
  busCode: string;
  registrationNumber: string;
  manufacturer: string;
  model: string;
  busType: string;
  capacity: number;
  availableSeats: number;
  status: string; // LIVE, SCHEDULED, NOT_ACTIVE
  driverName: string;
  routeName: string;
  currentStop: string;
  nextStop: string;
  eta: number;
}

export const BusDetails: React.FC = () => {
  const { busId } = useParams<{ busId: string }>();
  const navigate = useNavigate();
  const [bus, setBus] = useState<BusDetailsData | null>(null);
  const [routeInfo, setRouteInfo] = useState<any | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const loadBusAndRoute = async () => {
    try {
      const busRes = await axios.get(`/api/student/buses/${busId}`);
      setBus(busRes.data);

      // Find route details
      const routesRes = await axios.get('/api/student/routes');
      const matchedRoute = routesRes.data.find((r: any) => r.routeName === busRes.data.routeName);
      if (matchedRoute) {
        const routeIdRes = await axios.get(`/api/student/routes/${matchedRoute.id}`);
        setRouteInfo(routeIdRes.data);
      }
      setError('');
    } catch (err) {
      console.error(err);
      setError('Failed to fetch bus specification details');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadBusAndRoute();
  }, [busId]);

  if (loading) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[60vh] gap-3 text-brandTextSecondary">
        <div className="w-8 h-8 border-4 border-brandBlue border-t-transparent rounded-full animate-spin"></div>
        <p className="text-xs font-bold">Loading Coach Details...</p>
      </div>
    );
  }

  // Determine Stop Status (COMPLETED, CURRENT, NEXT, UPCOMING)
  const getStopStatus = (stopName: string, sequence: number) => {
    if (!bus) return 'UPCOMING';
    if (bus.status === 'NOT_ACTIVE' || bus.status === 'SCHEDULED') {
      return sequence === 1 ? 'CURRENT' : 'UPCOMING';
    }

    if (stopName === bus.currentStop) return 'CURRENT';
    if (stopName === bus.nextStop) return 'NEXT';

    // Sequence comparisons
    if (routeInfo && routeInfo.stops) {
      const currentStopObj = routeInfo.stops.find((s: any) => s.stopName === bus.currentStop);
      if (currentStopObj) {
        if (sequence < currentStopObj.sequence) return 'COMPLETED';
        return 'UPCOMING';
      }
    }
    return 'UPCOMING';
  };

  return (
    <div className="flex flex-col gap-6 pb-4">
      {/* Header and Back Button */}
      <div className="flex items-center justify-between">
        <button
          onClick={() => navigate(-1)}
          className="flex items-center gap-1.5 p-2 bg-white border border-brandBorder rounded-xl text-brandTextSecondary hover:text-brandTextPrimary transition-all shadow-sm text-xs font-bold"
        >
          <ChevronLeft className="w-4 h-4" /> Back
        </button>
        <span className="text-[10px] text-brandTextSecondary font-bold uppercase tracking-wider">
          Bus Specification
        </span>
      </div>

      {error && (
        <div className="p-4 bg-brandRed/10 border border-brandRed/20 text-brandRed text-xs rounded-2xl font-bold">
          {error}
        </div>
      )}

      {bus && (
        <>
          {/* Main Info Card */}
          <div className="bg-white border border-brandBorder rounded-3xl p-5 flex flex-col gap-5 relative overflow-hidden text-brandTextPrimary shadow-sm">
            <div className="absolute right-0 top-0 w-32 h-32 bg-brandBlue/5 rounded-full blur-2xl"></div>

            <div className="flex justify-between items-start">
              <div>
                <span className="bg-brandBlue text-white font-extrabold text-[10px] px-3 py-1 rounded-full uppercase tracking-wider shadow-sm">
                  {bus.busNumber}
                </span>
                <h2 className="text-lg font-bold text-brandNavy mt-2">
                  {bus.model}
                </h2>
                <p className="text-xs text-brandTextSecondary font-bold mt-0.5">
                  {bus.manufacturer} • {bus.registrationNumber}
                </p>
              </div>
              <span className={`text-[10px] font-bold px-2 py-0.5 rounded-full ${
                bus.status === 'LIVE' 
                  ? 'bg-brandGreen/10 border border-brandGreen/25 text-brandGreen'
                  : 'bg-brandTextSecondary/10 border border-brandBorder text-brandTextSecondary'
              } uppercase`}>
                {bus.status}
              </span>
            </div>

            {/* Spec grid */}
            <div className="grid grid-cols-2 gap-4 border-t border-brandBorder pt-4 text-xs">
              <div className="bg-brandBg p-3 rounded-2xl border border-brandBorder">
                <p className="text-[9px] text-brandTextSecondary font-bold uppercase tracking-wider">
                  Coach Type
                </p>
                <p className="font-bold text-brandNavy mt-0.5">
                  {bus.busType === 'AC' ? 'Air Conditioned' : 'Non-AC'}
                </p>
              </div>
              <div className="bg-brandBg p-3 rounded-2xl border border-brandBorder">
                <p className="text-[9px] text-brandTextSecondary font-bold uppercase tracking-wider">
                  Total Seat Capacity
                </p>
                <p className="font-bold text-brandNavy mt-0.5">
                  {bus.capacity} Seats
                </p>
              </div>
              <div className="bg-brandBg p-3 rounded-2xl border border-brandBorder col-span-2 flex items-center gap-2">
                <Info className="w-4 h-4 text-brandAmber shrink-0" />
                <div>
                  <p className="text-[9px] text-brandTextSecondary font-bold uppercase tracking-wider">
                    Seat Attendance Telemetry
                  </p>
                  <p className="font-bold text-brandTextSecondary text-[10px] mt-0.5">
                    Not currently available (Requires ticketing integration)
                  </p>
                </div>
              </div>
            </div>
          </div>

          {/* Driver Card */}
          <div className="bg-white border border-brandBorder rounded-3xl p-5 flex items-center justify-between shadow-sm">
            <div className="flex items-center gap-3">
              <div className="p-3 bg-brandBg border border-brandBorder rounded-2xl text-brandTextSecondary">
                <UserIcon className="w-6 h-6" />
              </div>
              <div>
                <span className="text-[9px] text-brandTextSecondary font-bold uppercase tracking-wider">
                  Assigned Driver
                </span>
                <h3 className="font-bold text-brandNavy text-sm">
                  {bus.driverName}
                </h3>
              </div>
            </div>
            <div className="flex items-center gap-1 bg-brandAmber/10 border border-brandAmber/25 text-brandAmber text-xs font-bold px-2.5 py-1 rounded-xl">
              <Star className="w-3.5 h-3.5 fill-brandAmber text-brandAmber" />
              4.8
            </div>
          </div>

          {/* Route Progression Timeline */}
          {routeInfo && (
            <div>
              <h3 className="text-[10px] font-bold text-brandTextSecondary uppercase tracking-wider mb-4 px-1">
                Route Stop Progression
              </h3>

              <div className="flex flex-col relative pl-6 before:absolute before:left-[11px] before:top-2 before:bottom-2 before:w-[2px] before:bg-brandBorder">
                {routeInfo.stops.map((stop: any) => {
                  const status = getStopStatus(stop.stopName, stop.sequence);
                  
                  let dotColor = 'bg-brandBg border-brandBorder';
                  let textColor = 'text-brandTextSecondary';
                  let statusBadge = null;

                  if (status === 'COMPLETED') {
                    dotColor = 'bg-brandGreen border-white';
                    textColor = 'text-brandTextSecondary/70 font-medium';
                    statusBadge = (
                      <span className="text-[9px] font-bold text-brandGreen bg-brandGreen/10 border border-brandGreen/20 px-2 py-0.5 rounded-full uppercase">
                        Passed
                      </span>
                    );
                  } else if (status === 'CURRENT') {
                    dotColor = 'bg-brandBlue border-white ring-4 ring-brandBlue/10 scale-110';
                    textColor = 'text-brandNavy font-extrabold';
                    statusBadge = (
                      <span className="text-[9px] font-bold text-white bg-brandBlue px-2 py-0.5 rounded-full uppercase shadow-sm">
                        Current Stop
                      </span>
                    );
                  } else if (status === 'NEXT') {
                    dotColor = 'bg-brandAmber border-white ring-4 ring-brandAmber/10 scale-110';
                    textColor = 'text-brandAmber font-extrabold';
                    statusBadge = (
                      <span className="text-[9px] font-bold text-brandAmber bg-brandAmber/10 border border-brandAmber/20 px-2 py-0.5 rounded-full uppercase">
                        Next
                      </span>
                    );
                  } else {
                    dotColor = 'bg-brandBg border-brandBorder';
                    textColor = 'text-brandTextSecondary font-bold';
                  }

                  return (
                    <div key={stop.stopId} className="mb-6 last:mb-0 relative flex items-start justify-between">
                      {/* Left Dot */}
                      <span className={`absolute left-[-20px] top-[5px] w-[12px] h-[12px] rounded-full border-2 transition-all ${dotColor}`}></span>
                      
                      {/* Stop Info */}
                      <div>
                        <h4 className={`text-sm ${textColor}`}>{stop.stopName}</h4>
                        <p className="text-[10px] text-brandTextSecondary font-bold mt-0.5">
                          Sequence #{stop.sequence} • Expected: {stop.arrivalTime || 'N/A'}
                        </p>
                      </div>

                      {/* Right Tag */}
                      {statusBadge}
                    </div>
                  );
                })}
              </div>
            </div>
          )}

          {/* Action Track Bus */}
          {bus.status === 'LIVE' && (
            <button
              onClick={() => navigate(`/student/live-map?busId=${bus.busId}`)}
              className="w-full bg-brandBlue hover:bg-brandBlue/90 text-white font-bold py-3 px-4 rounded-xl shadow-md flex items-center justify-center gap-2 transition-all mt-4 uppercase text-xs tracking-wider"
            >
              <MapPin className="w-4 h-4" /> TRACK LIVE BUS
            </button>
          )}
        </>
      )}
    </div>
  );
};
