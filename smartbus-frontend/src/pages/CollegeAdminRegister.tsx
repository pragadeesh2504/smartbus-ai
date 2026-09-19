import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import axios from 'axios';
import { School, Mail, Lock, User, Phone, CheckCircle, ArrowLeft, Building2, ShieldCheck } from 'lucide-react';

export const CollegeAdminRegister: React.FC = () => {
  const navigate = useNavigate();

  const [collegeName, setCollegeName] = useState('');
  const [collegeCode, setCollegeCode] = useState('');
  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [phone, setPhone] = useState('');

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState(false);
  const [registeredCode, setRegisteredCode] = useState('');

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError('');

    try {
      const res = await axios.post('/api/auth/register/admin', {
        collegeName: collegeName.trim(),
        collegeCode: collegeCode.trim().toUpperCase(),
        firstName: firstName.trim(),
        lastName: lastName.trim(),
        email: email.trim().toLowerCase(),
        password,
        phoneNumber: phone.trim() ? phone.trim() : undefined
      });

      setRegisteredCode(collegeCode.trim().toUpperCase());
      setSuccess(true);
    } catch (err: any) {
      setError(
        err.response?.data?.message ||
        'Registration failed. Please verify that the college name, code, and admin email are unique.'
      );
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-brandBg flex items-center justify-center p-4">
      {/* Background Accent Gradients */}
      <div className="absolute top-0 left-0 w-full h-full overflow-hidden pointer-events-none z-0">
        <div className="absolute top-[-20%] left-[-10%] w-[50%] h-[60%] rounded-full bg-brandBlue/5 blur-[120px]"></div>
        <div className="absolute bottom-[-10%] right-[-10%] w-[45%] h-[55%] rounded-full bg-brandTeal/5 blur-[120px]"></div>
      </div>

      <div className="w-full max-w-xl bg-white border border-brandBorder p-8 rounded-3xl shadow-xl relative z-10 my-8">
        {/* Top accent line */}
        <div className="absolute top-0 left-0 right-0 h-1.5 bg-gradient-to-r from-brandNavy via-brandBlue to-brandTeal rounded-t-3xl"></div>

        <div className="text-center mb-6 mt-2">
          <div className="inline-flex p-3 bg-brandBlue/5 border border-brandBlue/10 rounded-2xl mb-3 text-brandBlue">
            <Building2 className="w-7 h-7" />
          </div>
          <h2 className="text-2xl font-bold tracking-tight text-brandNavy">Register College & Admin</h2>
          <p className="text-sm text-brandTextSecondary mt-1">
            Self-onboard your institution and set up the primary Transport Administrator account
          </p>
        </div>

        {error && (
          <div className="mb-4 p-3 bg-brandRed/10 border border-brandRed/20 text-brandRed rounded-xl text-xs text-center font-medium">
            {error}
          </div>
        )}

        {success ? (
          <div className="text-center py-6 space-y-4">
            <div className="inline-flex p-4 bg-emerald-50 text-emerald-600 rounded-full">
              <CheckCircle className="w-10 h-10" />
            </div>
            <h3 className="text-xl font-bold text-gray-900">Institution Registered Successfully!</h3>
            <p className="text-sm text-gray-600 max-w-md mx-auto">
              Your college <strong className="text-gray-900">{collegeName}</strong> with College Code{' '}
              <span className="font-mono font-bold text-brandBlue px-2 py-0.5 bg-brandBlue/5 rounded border border-brandBlue/20">
                {registeredCode}
              </span>{' '}
              and administrator account <strong className="text-gray-900">{email}</strong> have been created atomically.
            </p>
            <div className="p-4 bg-gray-50 border border-gray-200 rounded-2xl text-left text-xs text-gray-600 space-y-1 max-w-md mx-auto">
              <div className="flex items-center gap-1.5 font-semibold text-gray-800 mb-1">
                <ShieldCheck className="w-4 h-4 text-emerald-600" />
                What happens next?
              </div>
              <p>• Log in with your Admin Email and Password to access your College Transport Dashboard.</p>
              <p>• Your students will use College Code <strong className="font-mono text-gray-900">{registeredCode}</strong> to register and track buses.</p>
              <p>• You can add drivers, assign buses, and configure routes from the Admin portal.</p>
            </div>
            <div className="pt-3">
              <button
                onClick={() => navigate('/login')}
                className="w-full py-3 bg-brandBlue text-white font-semibold rounded-xl hover:bg-brandBlue/90 transition-all shadow-md"
              >
                Proceed to Admin Login
              </button>
            </div>
          </div>
        ) : (
          <form onSubmit={handleSubmit} className="space-y-4">
            {/* College Details Section */}
            <div className="p-4 bg-brandBg/60 border border-brandBorder rounded-2xl space-y-3">
              <div className="flex items-center gap-2 text-xs font-bold text-brandNavy uppercase tracking-wider">
                <School className="w-4 h-4 text-brandBlue" />
                Institution Details
              </div>

              <div>
                <label className="block text-xs font-semibold text-brandTextPrimary mb-1">College / University Name</label>
                <input
                  type="text"
                  value={collegeName}
                  onChange={e => setCollegeName(e.target.value)}
                  placeholder="e.g. Chennai Institute of Technology"
                  required
                  className="w-full px-3 py-2 border border-brandBorder bg-white rounded-xl text-sm text-brandTextPrimary focus:outline-none focus:border-brandBlue focus:ring-1 focus:ring-brandBlue transition-all"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-brandTextPrimary mb-1">
                  College Code <span className="text-gray-400 font-normal">(Students will use this code to register & log in)</span>
                </label>
                <input
                  type="text"
                  value={collegeCode}
                  onChange={e => setCollegeCode(e.target.value.toUpperCase())}
                  placeholder="e.g. CIT"
                  required
                  pattern="^[A-Za-z0-9_-]{2,20}$"
                  title="2 to 20 alphanumeric characters, dashes, or underscores"
                  className="w-full px-3 py-2 border border-brandBorder bg-white rounded-xl text-sm font-mono uppercase text-brandTextPrimary focus:outline-none focus:border-brandBlue focus:ring-1 focus:ring-brandBlue transition-all"
                />
                <p className="text-[10px] text-brandTextSecondary mt-1">2–20 characters. Must be unique across SmartBus.</p>
              </div>
            </div>

            {/* Primary Administrator Section */}
            <div className="p-4 bg-brandBg/60 border border-brandBorder rounded-2xl space-y-3">
              <div className="flex items-center gap-2 text-xs font-bold text-brandNavy uppercase tracking-wider">
                <User className="w-4 h-4 text-brandBlue" />
                Transport Administrator Details
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-brandTextPrimary mb-1">First Name</label>
                  <input
                    type="text"
                    value={firstName}
                    onChange={e => setFirstName(e.target.value)}
                    placeholder="Pragadeesh"
                    required
                    className="w-full px-3 py-2 border border-brandBorder bg-white rounded-xl text-sm text-brandTextPrimary focus:outline-none focus:border-brandBlue transition-all"
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-brandTextPrimary mb-1">Last Name</label>
                  <input
                    type="text"
                    value={lastName}
                    onChange={e => setLastName(e.target.value)}
                    placeholder="H"
                    required
                    className="w-full px-3 py-2 border border-brandBorder bg-white rounded-xl text-sm text-brandTextPrimary focus:outline-none focus:border-brandBlue transition-all"
                  />
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-brandTextPrimary mb-1">Official Admin Email</label>
                <div className="relative">
                  <span className="absolute inset-y-0 left-0 pl-3 flex items-center text-brandTextSecondary">
                    <Mail className="w-4 h-4" />
                  </span>
                  <input
                    type="email"
                    value={email}
                    onChange={e => setEmail(e.target.value)}
                    placeholder="transport@cit.edu"
                    required
                    className="w-full pl-9 pr-3 py-2 border border-brandBorder bg-white rounded-xl text-sm text-brandTextPrimary focus:outline-none focus:border-brandBlue transition-all"
                  />
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-brandTextPrimary mb-1">Admin Password</label>
                <div className="relative">
                  <span className="absolute inset-y-0 left-0 pl-3 flex items-center text-brandTextSecondary">
                    <Lock className="w-4 h-4" />
                  </span>
                  <input
                    type="password"
                    value={password}
                    onChange={e => setPassword(e.target.value)}
                    placeholder="••••••••"
                    required
                    minLength={8}
                    className="w-full pl-9 pr-3 py-2 border border-brandBorder bg-white rounded-xl text-sm text-brandTextPrimary focus:outline-none focus:border-brandBlue transition-all"
                  />
                </div>
                <p className="text-[10px] text-brandTextSecondary mt-0.5">Minimum 8 characters.</p>
              </div>

              <div>
                <label className="block text-xs font-semibold text-brandTextPrimary mb-1">Contact Phone Number</label>
                <div className="relative">
                  <span className="absolute inset-y-0 left-0 pl-3 flex items-center text-brandTextSecondary">
                    <Phone className="w-4 h-4" />
                  </span>
                  <input
                    type="tel"
                    value={phone}
                    onChange={e => setPhone(e.target.value)}
                    placeholder="+91 98765 43210"
                    className="w-full pl-9 pr-3 py-2 border border-brandBorder bg-white rounded-xl text-sm text-brandTextPrimary focus:outline-none focus:border-brandBlue transition-all"
                  />
                </div>
              </div>
            </div>

            <button
              type="submit"
              disabled={loading}
              className="w-full h-11 bg-brandBlue hover:bg-brandBlue/90 text-white font-semibold rounded-xl text-sm transition-all shadow-md flex items-center justify-center gap-2 mt-2"
            >
              {loading ? (
                <div className="w-5 h-5 border-2 border-white border-t-transparent rounded-full animate-spin" />
              ) : (
                'Register College & Create Admin'
              )}
            </button>

            <div className="text-center pt-2">
              <Link
                to="/login"
                className="inline-flex items-center gap-1.5 text-xs text-brandBlue hover:text-brandBlue/80 font-medium transition-colors"
              >
                <ArrowLeft className="w-3.5 h-3.5" />
                Already registered? Back to Login
              </Link>
            </div>
          </form>
        )}
      </div>
    </div>
  );
};

export default CollegeAdminRegister;