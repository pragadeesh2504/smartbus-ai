import React, { useEffect, useState, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import { Html5QrcodeScanner } from 'html5-qrcode';
import {
  Bus,
  MapPin,
  Clock,
  CheckCircle,
  AlertTriangle,
  Play,
  ArrowLeft
} from 'lucide-react';

export const Scan: React.FC = () => {
  const navigate = useNavigate();
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [successData, setSuccessData] = useState<any>(null);
  const [loading, setLoading] = useState(false);
  const [startLoading, setStartLoading] = useState(false);
  
  // Developer mock input field
  const [mockQr, setMockQr] = useState('');

  const scannerRef = useRef<Html5QrcodeScanner | null>(null);

  useEffect(() => {
    // Start QR Scanner on mount
    const scanner = new Html5QrcodeScanner(
      "qr-reader",
      { fps: 10, qrbox: { width: 250, height: 250 } },
      /* verbose= */ false
    );
    scannerRef.current = scanner;

    scanner.render(
      (decodedText) => {
        // Stop scanning upon success
        scanner.clear().catch(err => console.error(err));
        handleQrPayload(decodedText);
      },
      (error) => {
        // Suppress continuous console logs for scanner mismatch frames
      }
    );

    return () => {
      if (scannerRef.current) {
        scannerRef.current.clear().catch(err => console.log('Scanner cleanup error', err));
      }
    };
  }, []);

  const handleQrPayload = async (payload: string) => {
    setErrorMsg(null);
    setLoading(true);
    try {
      const res = await axios.post('/api/driver/qr/verify', {
        qrContent: payload
      });
      setSuccessData(res.data);
    } catch (err: any) {
      if (err.response && err.response.data && err.response.data.message) {
        setErrorMsg(err.response.data.message);
      } else {
        setErrorMsg('QR_INVALID');
      }
    } finally {
      setLoading(false);
    }
  };

  const handleStartTrip = async () => {
    if (!successData?.schedule?.scheduleId) return;
    setStartLoading(true);
    try {
      const scheduleId = successData.schedule.scheduleId;
      await axios.post(`/api/driver/trips/${scheduleId}/start`);
      // Redirect to live trip console
      navigate('/driver/active');
    } catch (err: any) {
      alert(err.response?.data?.message || 'Failed to start trip');
    } finally {
      setStartLoading(false);
    }
  };

  const resetScanner = () => {
    setSuccessData(null);
    setErrorMsg(null);
    
    // Re-mount scanner
    setTimeout(() => {
      const scanner = new Html5QrcodeScanner(
        "qr-reader",
        { fps: 10, qrbox: { width: 250, height: 250 } },
        false
      );
      scannerRef.current = scanner;
      scanner.render(
        (decodedText) => {
          scanner.clear().catch(err => console.error(err));
          handleQrPayload(decodedText);
        },
        (error) => {}
      );
    }, 100);
  };

  return (
    <div className="space-y-6">
      {/* Top navbar indicator */}
      <div className="flex items-center gap-3">
        <button
          onClick={() => navigate('/driver/dashboard')}
          className="p-2 bg-white border border-brandBorder rounded-xl text-brandTextSecondary hover:text-brandTextPrimary transition-all shadow-sm"
        >
          <ArrowLeft className="w-4 h-4" />
        </button>
        <h2 className="text-base font-bold text-brandNavy">Verify Transit Bus</h2>
      </div>

      {loading && (
        <div className="bg-white border border-brandBorder rounded-3xl p-8 flex flex-col items-center justify-center space-y-4 shadow-sm">
          <div className="animate-spin rounded-full h-8 w-8 border-t-2 border-brandBlue"></div>
          <p className="text-xs text-brandTextSecondary font-semibold">Verifying bus credentials on server...</p>
        </div>
      )}

      {/* 1. Success Verified View */}
      {successData && !loading && (
        <div className="bg-white border border-brandBorder rounded-3xl p-6 space-y-6 shadow-sm">
          <div className="flex flex-col items-center justify-center text-center space-y-3 pb-4 border-b border-brandBorder">
            <div className="p-3 bg-brandGreen/10 border border-brandGreen/20 rounded-full text-brandGreen animate-bounce">
              <CheckCircle className="w-10 h-10" />
            </div>
            <div>
              <h3 className="text-base font-bold text-brandNavy">Bus Verified Successfully</h3>
              <p className="text-xs text-brandTextSecondary font-bold mt-0.5">Credentials match schedule parameters</p>
            </div>
          </div>

          <div className="space-y-4">
            <div className="bg-brandBg border border-brandBorder p-4 rounded-2xl flex items-center gap-3">
              <Bus className="w-6 h-6 text-brandBlue shrink-0" />
              <div>
                <p className="text-[10px] text-brandTextSecondary font-bold uppercase">Bus Identifier</p>
                <p className="text-sm font-extrabold text-brandNavy">
                  {successData.bus.busNumber} ({successData.bus.busCode})
                </p>
              </div>
            </div>

            <div className="bg-brandBg border border-brandBorder p-4 rounded-2xl flex items-center gap-3">
              <MapPin className="w-6 h-6 text-brandBlue shrink-0" />
              <div>
                <p className="text-[10px] text-brandTextSecondary font-bold uppercase">Transit Route</p>
                <p className="text-xs font-bold text-brandNavy truncate">{successData.schedule.routeName}</p>
              </div>
            </div>

            <div className="bg-brandBg border border-brandBorder p-4 rounded-2xl flex items-center gap-3">
              <Clock className="w-6 h-6 text-brandBlue shrink-0" />
              <div>
                <p className="text-[10px] text-brandTextSecondary font-bold uppercase">Departure Time</p>
                <p className="text-sm font-extrabold text-brandNavy">{successData.schedule.departureTime.slice(0, 5)}</p>
              </div>
            </div>
          </div>

          <button
            onClick={handleStartTrip}
            disabled={startLoading}
            className="w-full h-14 bg-brandGreen hover:bg-brandGreen/90 disabled:bg-brandGreen/50 text-white font-extrabold rounded-2xl shadow-md flex items-center justify-center gap-3 transition-all text-base uppercase tracking-wider"
          >
            <Play className="w-5 h-5 fill-white" />
            {startLoading ? 'Initializing...' : 'Start Active Trip'}
          </button>
        </div>
      )}

      {/* 2. Failure View */}
      {errorMsg && !loading && (
        <div className="bg-white border border-brandBorder rounded-3xl p-6 space-y-6 shadow-sm text-center">
          <div className="p-4 bg-brandRed/10 border border-brandRed/20 rounded-full text-brandRed w-16 h-16 mx-auto flex items-center justify-center shadow-sm">
            <AlertTriangle className="w-8 h-8" />
          </div>
          <div>
            <h3 className="text-base font-bold text-brandNavy">Verification Failed</h3>
            <p className="text-xs text-brandRed font-extrabold mt-1 uppercase tracking-wider">{errorMsg}</p>
            <p className="text-xs text-brandTextSecondary font-semibold max-w-xs mx-auto mt-2">
              The credentials on the scanned QR code could not be verified for today's schedule duty.
            </p>
          </div>
          <button
            onClick={resetScanner}
            className="w-full py-3.5 bg-brandBg hover:bg-brandBg/80 border border-brandBorder text-brandTextPrimary font-bold rounded-2xl transition-all text-sm shadow-sm"
          >
            Scan Again
          </button>
        </div>
      )}

      {/* 3. Scanner Camera Feed Box */}
      {!successData && !errorMsg && !loading && (
        <div className="space-y-6">
          <div className="bg-white border border-brandBorder rounded-3xl overflow-hidden p-6 shadow-sm">
            <div id="qr-reader" className="w-full overflow-hidden rounded-2xl bg-brandBg border border-brandBorder"></div>
            <p className="text-[11px] text-brandTextSecondary font-semibold text-center mt-4">
              Hold the QR code attached to the bus dashboard inside the scanning window.
            </p>
          </div>

          {/* Dev mock shortcut container */}
          <div className="bg-white border border-brandBorder rounded-3xl p-6 space-y-4 shadow-sm text-brandTextPrimary">
            <h4 className="text-xs font-bold uppercase tracking-wider text-brandTextSecondary flex items-center gap-2">
              <span className="w-1.5 h-1.5 bg-brandBlue rounded-full animate-pulse"></span>
              Development Mock Verification
            </h4>
            <div className="flex gap-2">
              <input
                type="text"
                value={mockQr}
                onChange={(e) => setMockQr(e.target.value)}
                placeholder="smartbus://bus/{code}/{token}"
                className="flex-1 bg-brandBg border border-brandBorder rounded-xl px-4 py-2.5 text-xs text-brandTextPrimary placeholder:text-brandTextSecondary/60 focus:outline-none focus:border-brandBlue transition-all font-semibold"
              />
              <button
                onClick={() => {
                  if (scannerRef.current) {
                    scannerRef.current.clear().catch(e => console.log(e));
                  }
                  handleQrPayload(mockQr);
                }}
                className="px-4 py-2 bg-brandBlue hover:bg-brandBlue/90 text-white rounded-xl text-xs font-bold transition-all shadow-sm shrink-0"
              >
                Submit Mock
              </button>
            </div>
            <p className="text-[10px] text-brandTextSecondary font-medium">
              Note: Paste a seeded QR token URL to verify without camera feeds.
            </p>
          </div>
        </div>
      )}
    </div>
  );
};
