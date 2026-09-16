/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        brandNavy: '#0B1F3A',
        brandBlue: '#2563EB',
        brandTeal: '#0EA5A4',
        brandGreen: '#16A34A',
        brandAmber: '#F59E0B',
        brandRed: '#DC2626',
        brandBg: '#F5F7FB',
        brandCard: '#FFFFFF',
        brandTextPrimary: '#0F172A',
        brandTextSecondary: '#64748B',
        brandBorder: '#E2E8F0',
        primary: {
          light: '#2563EB',
          DEFAULT: '#0B1F3A',
          dark: '#0B1F3A',
        },
      },
    },
  },
  plugins: [],
  darkMode: 'class',
}
