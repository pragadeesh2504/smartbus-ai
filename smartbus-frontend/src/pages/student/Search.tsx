import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import {
  Search as SearchIcon,
  Heart,
  Navigation,
  Bus as BusIcon,
  Sparkles
} from 'lucide-react';

interface BusSummary {
  busId: string;
  busNumber: string;
  busCode: string;
  routeName: string;
  status: string; // LIVE, SCHEDULED, NOT_ACTIVE
  currentStop: string;
  nextStop: string;
  eta: number;
}

export const Search: React.FC = () => {
  const navigate = useNavigate();
  const [buses, setBuses] = useState<BusSummary[]>([]);
  const [favorites, setFavorites] = useState<string[]>([]);
  const [searchQuery, setSearchQuery] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const fetchBusesAndFavorites = async () => {
    try {
      // Fetch favorites list
      const favsRes = await axios.get('/api/student/favorites');
      setFavorites(favsRes.data.map((b: any) => b.busId));

      // Fetch search results
      const res = await axios.get(`/api/student/buses/search?q=${encodeURIComponent(searchQuery)}`);
      setBuses(res.data);
      setError('');
    } catch (err) {
      console.error(err);
      setError('Failed to query active buses list');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchBusesAndFavorites();
  }, [searchQuery]);

  const handleFavoriteToggle = async (e: React.MouseEvent, busId: string) => {
    e.stopPropagation(); // prevent card click redirect
    const isFav = favorites.includes(busId);
    try {
      if (isFav) {
        await axios.delete(`/api/student/favorites/${busId}`);
        setFavorites(favorites.filter(id => id !== busId));
      } else {
        await axios.post(`/api/student/favorites/${busId}`);
        setFavorites([...favorites, busId]);
      }
    } catch (err) {
      console.error('Error toggling favorite:', err);
    }
  };

  const handleCardClick = (busId: string) => {
    navigate(`/student/bus/${busId}`);
  };

  return (
    <div className="flex flex-col gap-6 pb-4">
      {/* Title */}
      <div>
        <h1 className="text-xl font-bold tracking-tight text-brandNavy flex items-center gap-2">
          <Sparkles className="w-5 h-5 text-brandBlue animate-pulse" /> Search Buses & Routes
        </h1>
        <p className="text-xs text-brandTextSecondary mt-1 font-medium">
          Lookup active bus numbers, codes, or specific stops sequences.
        </p>
      </div>

      {/* Search Input Box */}
      <div className="relative">
        <span className="absolute inset-y-0 left-0 pl-3.5 flex items-center text-brandTextSecondary">
          <SearchIcon className="w-5 h-5" />
        </span>
        <input
          type="text"
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          placeholder="Search number, code, route, stop..."
          className="w-full bg-white border border-brandBorder focus:border-brandBlue focus:ring-1 focus:ring-brandBlue rounded-2xl py-3.5 pl-11 pr-4 text-sm text-brandTextPrimary placeholder:text-brandTextSecondary/60 outline-none transition-all shadow-sm font-semibold"
        />
      </div>

      {error && (
        <div className="p-4 bg-brandRed/10 border border-brandRed/20 text-brandRed text-xs rounded-2xl font-bold">
          {error}
        </div>
      )}

      {/* Results List */}
      <div>
        <h3 className="text-[10px] font-bold text-brandTextSecondary uppercase tracking-wider mb-4 px-1">
          Buses Found ({buses.length})
        </h3>

        {loading && buses.length === 0 ? (
          <div className="flex justify-center py-8">
            <div className="w-6 h-6 border-2 border-brandBlue border-t-transparent rounded-full animate-spin"></div>
          </div>
        ) : buses.length > 0 ? (
          <div className="flex flex-col gap-4">
            {buses.map((bus) => {
              const isFav = favorites.includes(bus.busId);
              return (
                <div
                  key={bus.busId}
                  onClick={() => handleCardClick(bus.busId)}
                  className="bg-white border border-brandBorder hover:border-brandBlue/35 hover:bg-brandBlue/5 rounded-3xl p-5 flex flex-col gap-3.5 cursor-pointer transition-all shadow-sm group"
                >
                  {/* Top line */}
                  <div className="flex justify-between items-center">
                    <div className="flex items-center gap-2">
                      <div className="p-2 bg-brandBg border border-brandBorder rounded-xl text-brandTextSecondary group-hover:text-brandBlue group-hover:bg-brandBlue/5 transition-all">
                        <BusIcon className="w-5 h-5" />
                      </div>
                      <div>
                        <div className="flex items-center gap-1.5">
                          <span className="font-extrabold text-brandNavy text-sm">
                            {bus.busNumber}
                          </span>
                          <span className={`w-1.5 h-1.5 rounded-full ${bus.status === 'LIVE' ? 'bg-brandGreen animate-pulse' : 'bg-brandTextSecondary/50'}`}></span>
                        </div>
                        <p className="text-[10px] text-brandTextSecondary font-bold">
                          Code: {bus.busCode}
                        </p>
                      </div>
                    </div>

                    <div className="flex items-center gap-2">
                      {/* Favorite Button */}
                      <button
                        onClick={(e) => handleFavoriteToggle(e, bus.busId)}
                        className={`p-2 rounded-xl border transition-all ${
                          isFav 
                            ? 'bg-brandRed/10 border-brandRed/25 text-brandRed' 
                            : 'bg-white border-brandBorder text-brandTextSecondary hover:text-brandRed hover:bg-brandRed/5'
                        }`}
                        title={isFav ? 'Remove Favorite' : 'Mark Favorite'}
                      >
                        <Heart className={`w-4 h-4 ${isFav ? 'fill-brandRed' : ''}`} />
                      </button>

                      {/* Track Button */}
                      {bus.status === 'LIVE' && (
                        <button
                          onClick={(e) => {
                            e.stopPropagation();
                            navigate(`/student/live-map?busId=${bus.busId}`);
                          }}
                          className="p-2 bg-brandBlue hover:bg-brandBlue/90 border border-brandBlue rounded-xl text-white shadow-sm transition-all"
                          title="Track Live"
                        >
                          <Navigation className="w-4 h-4" />
                        </button>
                      )}
                    </div>
                  </div>

                  {/* Route stop details */}
                  <div className="flex justify-between items-end border-t border-brandBorder pt-3 text-xs font-semibold">
                    <div>
                      <p className="text-[9px] text-brandTextSecondary font-bold uppercase tracking-wider mb-0.5">
                        Route
                      </p>
                      <p className="font-bold text-brandNavy">
                        {bus.routeName}
                      </p>
                    </div>
                    
                    {bus.status === 'LIVE' && (
                      <div className="text-right">
                        <p className="text-[9px] text-brandTextSecondary font-bold mb-0.5">Next stop / ETA</p>
                        <p className="font-bold text-brandBlue">
                          {bus.nextStop} • {bus.eta >= 0 ? `${bus.eta} min` : 'N/A'}
                        </p>
                      </div>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        ) : (
          <div className="bg-white border border-brandBorder rounded-2xl p-6 text-center text-brandTextSecondary font-bold shadow-sm">
            No matching active buses or routes found.
          </div>
        )}
      </div>
    </div>
  );
};
