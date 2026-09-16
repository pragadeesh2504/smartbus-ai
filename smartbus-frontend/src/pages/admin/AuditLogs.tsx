import React, { useState, useEffect } from 'react';
import axios from 'axios';
import {
  Search,
  ChevronLeft,
  ChevronRight
} from 'lucide-react';

interface AuditLogType {
  id: string;
  userEmail: string;
  action: string;
  details: string;
  ipAddress: string;
  createdAt: string;
  entityName: string | null;
  entityId: string | null;
  oldValue: string | null;
  newValue: string | null;
}

export const AuditLogs: React.FC = () => {
  const [logs, setLogs] = useState<AuditLogType[]>([]);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [page, setPage] = useState(0);
  const [size] = useState(15);
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const fetchLogs = async () => {
    try {
      setLoading(true);
      const res = await axios.get('/api/admin/audit-logs', {
        params: { page, size, search }
      });
      if (res.data.success) {
        setLogs(res.data.data.content);
        setTotalElements(res.data.data.totalElements);
        setTotalPages(res.data.data.totalPages);
      }
    } catch (e) {
      setError('Failed to fetch system audit logs.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchLogs();
  }, [page]);

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(0);
    fetchLogs();
  };

  return (
    <div className="space-y-6">
      {/* Page Header */}
      <div className="flex justify-between items-center">
        <div>
          <h2 className="text-xl font-bold text-brandNavy tracking-tight">System Audit Registry</h2>
          <p className="text-xs text-brandTextSecondary mt-0.5 font-medium">Chronological mutation logs across all college transit resources</p>
        </div>
      </div>

      {error && (
        <div className="p-4 bg-brandRed/10 border border-brandRed/20 text-brandRed rounded-2xl text-xs font-bold">
          {error}
        </div>
      )}

      {/* Filter / Search Bar */}
      <div className="bg-white p-4 rounded-2xl border border-brandBorder flex justify-between items-center shadow-sm">
        <form onSubmit={handleSearchSubmit} className="relative w-full md:w-80">
          <span className="absolute inset-y-0 left-0 pl-3 flex items-center text-brandTextSecondary">
            <Search className="w-4 h-4" />
          </span>
          <input
            type="text"
            placeholder="Search by action, details, email..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="w-full pl-9 pr-3 py-2 bg-brandBg border border-brandBorder rounded-xl text-xs text-brandTextPrimary placeholder:text-brandTextSecondary/60 focus:outline-none focus:border-brandBlue focus:ring-1 focus:ring-brandBlue transition-all"
          />
        </form>
      </div>

      {/* Main Table */}
      <div className="bg-white rounded-2xl border border-brandBorder overflow-hidden shadow-sm">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs">
            <thead>
              <tr className="bg-brandBg border-b border-brandBorder text-brandTextSecondary uppercase text-[9px] tracking-wider font-bold">
                <th className="py-3.5 px-4">Operator</th>
                <th className="py-3.5 px-4">Action</th>
                <th className="py-3.5 px-4">Details</th>
                <th className="py-3.5 px-4">Entity</th>
                <th className="py-3.5 px-4">Before Value</th>
                <th className="py-3.5 px-4">After Value</th>
                <th className="py-3.5 px-4">Recorded At</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr>
                  <td colSpan={7} className="text-center py-12">
                    <div className="animate-spin rounded-full h-8 w-8 border-t-2 border-brandBlue mx-auto"></div>
                  </td>
                </tr>
              ) : logs.length > 0 ? (
                logs.map((log) => (
                  <tr key={log.id} className="border-b border-brandBorder last:border-0 hover:bg-brandBlue/5 transition-all text-brandTextPrimary font-medium">
                    <td className="py-3.5 px-4 font-bold text-brandNavy">{log.userEmail}</td>
                    <td className="py-3.5 px-4">
                      <span className="inline-flex px-2 py-0.5 bg-brandBg border border-brandBorder text-brandTextPrimary rounded-md font-bold text-[10px] uppercase">
                        {log.action}
                      </span>
                    </td>
                    <td className="py-3.5 px-4 text-brandTextSecondary font-semibold">{log.details}</td>
                    <td className="py-3.5 px-4">
                      {log.entityName ? (
                        <div>
                          <p className="font-bold text-brandTextPrimary">{log.entityName}</p>
                          <p className="text-[10px] text-brandTextSecondary font-mono truncate max-w-[80px]" title={log.entityId || ''}>
                            {log.entityId?.substring(0, 8)}...
                          </p>
                        </div>
                      ) : (
                        <span className="text-brandTextSecondary/60">-</span>
                      )}
                    </td>
                    <td className="py-3.5 px-4 font-mono text-brandRed font-semibold max-w-[120px] truncate" title={log.oldValue || ''}>
                      {log.oldValue || '-'}
                    </td>
                    <td className="py-3.5 px-4 font-mono text-brandGreen font-semibold max-w-[120px] truncate" title={log.newValue || ''}>
                      {log.newValue || '-'}
                    </td>
                    <td className="py-3.5 px-4 text-[10px] font-bold text-brandTextSecondary">
                      {new Date(log.createdAt).toLocaleString()}
                    </td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td colSpan={7} className="text-center py-10 text-brandTextSecondary font-medium">
                    No system audit logs found.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>

        {totalPages > 1 && (
          <div className="p-4 border-t border-brandBorder flex justify-between items-center text-brandTextSecondary text-xs">
            <span className="font-semibold">Showing {logs.length} of {totalElements} logs</span>
            <div className="flex gap-2">
              <button
                disabled={page === 0}
                onClick={() => setPage(page - 1)}
                className="p-1.5 bg-white border border-brandBorder hover:bg-brandBg disabled:opacity-30 rounded-lg text-brandTextPrimary font-bold transition-colors"
              >
                <ChevronLeft className="w-4 h-4" />
              </button>
              <button
                disabled={page === totalPages - 1}
                onClick={() => setPage(page + 1)}
                className="p-1.5 bg-white border border-brandBorder hover:bg-brandBg disabled:opacity-30 rounded-lg text-brandTextPrimary font-bold transition-colors"
              >
                <ChevronRight className="w-4 h-4" />
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
