import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import axios from 'axios';
import { Shield, Mail, ArrowLeft, RefreshCw, CheckCircle2 } from 'lucide-react';

export const ForgotPassword: React.FC = () => {
  const [email, setEmail] = useState('');
  const [loading, setLoading] = useState(false);
  const [submitted, setSubmitted] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError('');

    try {
      const response = await axios.post('/api/auth/forgot-password', { email });
      setMessage(response.data?.message || 'If an account exists with that email, a password reset link has been sent.');
      setSubmitted(true);
    } catch (err: any) {
      if (err.response?.status === 429) {
        setError('Too many requests. Please try again later.');
      } else {
        setMessage('If an account exists with that email, a password reset link has been sent.');
        setSubmitted(true);
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-brandBg flex items-center justify-center p-4">
      <div className="absolute top-0 left-0 w-full h-full overflow-hidden pointer-events-none z-0">
        <div className="absolute top-[-20%] left-[-10%] w-[50%] h-[60%] rounded-full bg-brandBlue/5 blur-[120px]"></div>
        <div className="absolute bottom-[-10%] right-[-10%] w-[45%] h-[55%] rounded-full bg-brandTeal/5 blur-[120px]"></div>
      </div>

      <div className="w-full max-w-md bg-white border border-brandBorder p-8 rounded-3xl shadow-xl relative z-10">
        <div className="absolute top-0 left-0 right-0 h-1.5 bg-gradient-to-r from-brandNavy via-brandBlue to-brandTeal rounded-t-3xl"></div>

        <div className="text-center mb-6 mt-2">
          <div className="inline-flex p-3 bg-brandBlue/5 border border-brandBlue/10 rounded-2xl mb-3 text-brandBlue">
            <Shield className="w-7 h-7" />
          </div>
          <h2 className="text-2xl font-bold tracking-tight text-brandNavy">Reset Password</h2>
          <p className="text-sm text-brandTextSecondary mt-1">Enter your email to receive a password reset link</p>
        </div>

        {error && (
          <div className="mb-4 p-3 bg-brandRed/10 border border-brandRed/20 text-brandRed rounded-xl text-xs text-center font-medium">
            {error}
          </div>
        )}

        {submitted ? (
          <div className="text-center py-4">
            <div className="inline-flex p-3 bg-brandGreen/10 border border-brandGreen/20 text-brandGreen rounded-full mb-3">
              <CheckCircle2 className="w-8 h-8" />
            </div>
            <h3 className="text-base font-semibold text-brandNavy mb-2">Check Your Email</h3>
            <p className="text-xs text-brandTextSecondary leading-relaxed mb-6">
              {message}
            </p>
            <Link
              to="/login"
              className="inline-flex items-center justify-center gap-2 w-full h-11 bg-brandBlue hover:bg-brandBlue/90 text-white font-semibold rounded-xl text-sm transition-all shadow-md"
            >
              <ArrowLeft className="w-4 h-4" />
              Back to Login
            </Link>
          </div>
        ) : (
          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label className="block text-xs font-semibold text-brandTextPrimary mb-1">Email Address</label>
              <div className="relative">
                <span className="absolute inset-y-0 left-0 pl-3 flex items-center text-brandTextSecondary">
                  <Mail className="w-4 h-4" />
                </span>
                <input
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  required
                  placeholder="Enter your registered email"
                  className="w-full pl-9 pr-3 py-2.5 border border-brandBorder rounded-xl text-sm text-brandTextPrimary focus:outline-none focus:border-brandBlue focus:ring-1 focus:ring-brandBlue transition-all bg-brandBg/30"
                />
              </div>
            </div>

            <button
              type="submit"
              disabled={loading}
              className="w-full h-11 bg-brandBlue hover:bg-brandBlue/90 text-white font-semibold rounded-xl text-sm transition-all shadow-md flex items-center justify-center gap-2"
            >
              {loading ? (
                <RefreshCw className="w-4 h-4 animate-spin" />
              ) : (
                'Send Reset Link'
              )}
            </button>

            <div className="pt-4 text-center border-t border-brandBorder">
              <Link
                to="/login"
                className="inline-flex items-center text-xs font-semibold text-brandBlue hover:text-brandBlue/80 gap-1.5 transition-colors"
              >
                <ArrowLeft className="w-3.5 h-3.5" />
                Back to Login
              </Link>
            </div>
          </form>
        )}
      </div>
    </div>
  );
};