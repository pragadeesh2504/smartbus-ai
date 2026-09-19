import React, { useState, useEffect } from 'react';
import { useNavigate, useSearchParams, Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { Shield, Mail, Lock, User, Phone, KeyRound, School, CheckCircle, ArrowLeft } from 'lucide-react';

export const StudentRegister: React.FC = () => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { register } = useAuth();

  const [collegeCode, setCollegeCode] = useState('');
  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [phone, setPhone] = useState('');
  const [studentId, setStudentId] = useState('');
  const [department, setDepartment] = useState('');
  const [batch, setBatch] = useState('');

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState(false);

  useEffect(() => {
    const codeParam = searchParams.get('college');
    if (codeParam) {
      setCollegeCode(codeParam.trim().toUpperCase());
    }
  }, [searchParams]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError('');

    try {
      await register({
        email,
        password,
        firstName,
        lastName,
        phoneNumber: phone,
        role: 'STUDENT',
        studentId,
        department,
        batch,
        collegeCode: collegeCode.trim().toUpperCase()
      });
      setSuccess(true);
    } catch (err: any) {
      setError(
        err.response?.data?.message ||
        'Registration failed. Please verify your College Code and try again.'
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

      <div className="w-full max-w-lg bg-white border border-brandBorder p-8 rounded-3xl shadow-xl relative z-10 my-8">
        {/* Top accent line */}
        <div className="absolute top-0 left-0 right-0 h-1.5 bg-gradient-to-r from-brandNavy via-brandBlue to-brandTeal rounded-t-3xl"></div>

        <div className="text-center mb-6 mt-2">
          <div className="inline-flex p-3 bg-brandBlue/5 border border-brandBlue/10 rounded-2xl mb-3 text-brandBlue">
            <School className="w-7 h-7" />
          </div>
          <h2 className="text-2xl font-bold tracking-tight text-brandNavy">Student Registration</h2>
          <p className="text-sm text-brandTextSecondary mt-1">Join your college fleet tracking system</p>
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
            <h3 className="text-lg font-bold text-gray-900">Registration Successful!</h3>
            <p className="text-sm text-gray-600 max-w-sm mx-auto">
              Your student account has been created and securely linked to your college. You can now log in using your college email.
            </p>
            <div className="pt-2">
              <button
                onClick={() => navigate('/login')}
                className="w-full py-3 bg-brandBlue text-white font-semibold rounded-xl hover:bg-brandBlue/90 transition-all shadow-md"
              >
                Proceed to Login
              </button>
            </div>
          </div>
        ) : (
          <form onSubmit={handleSubmit} className="space-y-4">
            {/* College Code Field */}
            <div className="p-4 bg-blue-50/60 border border-blue-200 rounded-2xl">
              <label className="block text-xs font-bold text-blue-900 mb-1 flex items-center gap-1.5">
                <School className="w-4 h-4 text-brandBlue" />
                College Code
              </label>
              <input
                type="text"
                value={collegeCode}
                onChange={(e) => setCollegeCode(e.target.value.toUpperCase())}
                placeholder="e.g. CIT"
                required
                className="w-full px-3 py-2.5 bg-white border border-blue-300 rounded-xl text-sm font-mono tracking-wider font-semibold text-brandNavy uppercase focus:outline-none focus:border-brandBlue focus:ring-1 focus:ring-brandBlue transition-all"
              />
              <p className="text-[11px] text-blue-700/80 mt-1.5">
                Enter your institution's official college code (e.g., CIT).
              </p>
            </div>

            {/* Name Fields */}
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-xs font-semibold text-brandTextPrimary mb-1">First Name</label>
                <div className="relative">
                  <span className="absolute inset-y-0 left-0 pl-3 flex items-center text-brandTextSecondary">
                    <User className="w-4 h-4" />
                  </span>
                  <input
                    type="text"
                    value={firstName}
                    onChange={(e) => setFirstName(e.target.value)}
                    required
                    placeholder="First Name"
                    className="w-full pl-9 pr-3 py-2 border border-brandBorder rounded-xl text-sm text-brandTextPrimary focus:outline-none focus:border-brandBlue transition-all bg-brandBg/30"
                  />
                </div>
              </div>
              <div>
                <label className="block text-xs font-semibold text-brandTextPrimary mb-1">Last Name</label>
                <div className="relative">
                  <span className="absolute inset-y-0 left-0 pl-3 flex items-center text-brandTextSecondary">
                    <User className="w-4 h-4" />
                  </span>
                  <input
                    type="text"
                    value={lastName}
                    onChange={(e) => setLastName(e.target.value)}
                    required
                    placeholder="Last Name"
                    className="w-full pl-9 pr-3 py-2 border border-brandBorder rounded-xl text-sm text-brandTextPrimary focus:outline-none focus:border-brandBlue transition-all bg-brandBg/30"
                  />
                </div>
              </div>
            </div>

            {/* Email */}
            <div>
              <label className="block text-xs font-semibold text-brandTextPrimary mb-1">College Email Address</label>
              <div className="relative">
                <span className="absolute inset-y-0 left-0 pl-3 flex items-center text-brandTextSecondary">
                  <Mail className="w-4 h-4" />
                </span>
                <input
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="student@college.edu"
                  required
                  className="w-full pl-9 pr-3 py-2.5 border border-brandBorder rounded-xl text-sm text-brandTextPrimary focus:outline-none focus:border-brandBlue transition-all bg-brandBg/30"
                />
              </div>
            </div>

            {/* Password */}
            <div>
              <label className="block text-xs font-semibold text-brandTextPrimary mb-1">Password</label>
              <div className="relative">
                <span className="absolute inset-y-0 left-0 pl-3 flex items-center text-brandTextSecondary">
                  <Lock className="w-4 h-4" />
                </span>
                <input
                  type="password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="Create a strong password"
                  required
                  className="w-full pl-9 pr-3 py-2.5 border border-brandBorder rounded-xl text-sm text-brandTextPrimary focus:outline-none focus:border-brandBlue transition-all bg-brandBg/30"
                />
              </div>
            </div>

            {/* Phone */}
            <div>
              <label className="block text-xs font-semibold text-brandTextPrimary mb-1">Phone Number</label>
              <div className="relative">
                <span className="absolute inset-y-0 left-0 pl-3 flex items-center text-brandTextSecondary">
                  <Phone className="w-4 h-4" />
                </span>
                <input
                  type="text"
                  value={phone}
                  onChange={(e) => setPhone(e.target.value)}
                  placeholder="e.g. +91 9876543210"
                  required
                  className="w-full pl-9 pr-3 py-2.5 border border-brandBorder rounded-xl text-sm text-brandTextPrimary focus:outline-none focus:border-brandBlue transition-all bg-brandBg/30"
                />
              </div>
            </div>

            {/* Academic Details */}
            <div className="p-4 bg-brandBg border border-brandBorder rounded-2xl space-y-3">
              <div>
                <label className="block text-xs font-semibold text-brandTextPrimary mb-1">Student Roll Number / Register ID</label>
                <input
                  type="text"
                  value={studentId}
                  onChange={(e) => setStudentId(e.target.value)}
                  placeholder="e.g. 71762104001"
                  required
                  className="w-full px-3 py-2 border border-brandBorder bg-white rounded-xl text-sm text-brandTextPrimary focus:outline-none focus:border-brandBlue transition-all"
                />
              </div>

              <div className="grid grid-cols-2 gap-2">
                <div>
                  <label className="block text-xs font-semibold text-brandTextPrimary mb-1">Department</label>
                  <input
                    type="text"
                    value={department}
                    onChange={(e) => setDepartment(e.target.value)}
                    placeholder="e.g. CSE / IT / ECE"
                    required
                    className="w-full px-3 py-2 border border-brandBorder bg-white rounded-xl text-sm text-brandTextPrimary focus:outline-none focus:border-brandBlue transition-all"
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-brandTextPrimary mb-1">Batch Year</label>
                  <input
                    type="text"
                    value={batch}
                    onChange={(e) => setBatch(e.target.value)}
                    placeholder="e.g. 2023-27"
                    required
                    className="w-full px-3 py-2 border border-brandBorder bg-white rounded-xl text-sm text-brandTextPrimary focus:outline-none focus:border-brandBlue transition-all"
                  />
                </div>
              </div>
            </div>

            <button
              type="submit"
              disabled={loading}
              className="w-full py-3 bg-brandBlue text-white font-semibold rounded-xl hover:bg-brandBlue/90 transition-all shadow-md flex items-center justify-center gap-2 mt-2 disabled:opacity-60"
            >
              {loading ? (
                <div className="w-5 h-5 border-2 border-white/30 border-t-white rounded-full animate-spin" />
              ) : (
                'Register with College'
              )}
            </button>

            <div className="pt-2 text-center">
              <Link
                to="/login"
                className="text-xs text-brandBlue hover:text-brandBlue/80 font-medium inline-flex items-center gap-1 transition-colors"
              >
                <ArrowLeft className="w-3.5 h-3.5" /> Already registered? Back to Login
              </Link>
            </div>
          </form>
        )}
      </div>
    </div>
  );
};
