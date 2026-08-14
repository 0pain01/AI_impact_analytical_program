/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        // KPMG brand palette. `kpmg` (KPMG Blue, anchored on #00338D at the
        // 600 step) replaces indigo as the primary accent; `cobalt` (KPMG
        // Cobalt, anchored on #0091DA at 600) replaces violet/fuchsia as the
        // secondary accent. Mirrors the theme used in the Reverse Engineering
        // Platform app.
        kpmg: {
          50: '#f0f5ff',
          100: '#dce8fe',
          200: '#b9d2fd',
          300: '#88b2fc',
          400: '#478aff',
          500: '#0a63ff',
          600: '#00338d',
          700: '#002a74',
          800: '#00225d',
          900: '#001b49',
        },
        cobalt: {
          50: '#f0faff',
          100: '#dcf3fe',
          200: '#b9e7fd',
          300: '#88d5fc',
          400: '#47c2ff',
          500: '#0aadff',
          600: '#0091da',
          700: '#0077b3',
          800: '#006090',
          900: '#004b71',
        },
      },
      keyframes: {
        'fade-in-up': {
          '0%': { opacity: '0', transform: 'translateY(24px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' },
        },
        blob: {
          '0%, 100%': { transform: 'translate(0px, 0px) scale(1)' },
          '33%': { transform: 'translate(30px, -40px) scale(1.1)' },
          '66%': { transform: 'translate(-20px, 20px) scale(0.95)' },
        },
        float: {
          '0%, 100%': { transform: 'translateY(0px)' },
          '50%': { transform: 'translateY(-12px)' },
        },
        marquee: {
          '0%': { transform: 'translateX(0)' },
          '100%': { transform: 'translateX(-50%)' },
        },
        shimmer: {
          '0%': { backgroundPosition: '200% 0' },
          '100%': { backgroundPosition: '-200% 0' },
        },
        'node-pulse': {
          '0%, 100%': { opacity: '0.25', transform: 'scale(1)' },
          '50%': { opacity: '0.9', transform: 'scale(1.6)' },
        },
        'signal-flow': {
          '0%': { strokeDashoffset: '40' },
          '100%': { strokeDashoffset: '0' },
        },
      },
      animation: {
        'fade-in-up': 'fade-in-up 0.7s cubic-bezier(0.16, 1, 0.3, 1) forwards',
        blob: 'blob 12s infinite ease-in-out',
        float: 'float 6s ease-in-out infinite',
        marquee: 'marquee 28s linear infinite',
        shimmer: 'shimmer 3s linear infinite',
        'node-pulse': 'node-pulse 3.5s ease-in-out infinite',
        'signal-flow': 'signal-flow 2.4s linear infinite',
      },
    },
  },
  plugins: [],
}
