module.exports = {
  content: [
    './src/**/*',
  ],
  theme: {
    extend: {
      keyframes: {
        wiggle: {
          '0%, 100%': { transform: 'rotate(-10deg)' },
          '50%': { transform: 'rotate(10deg)' },
        },
        'pulse-ring': {
          '0%, 100%': { boxShadow: '0 0 4px 2px rgba(250, 204, 21, 0.3)' },
          '50%': { boxShadow: '0 0 10px 5px rgba(250, 204, 21, 0.6)' },
        },
      },
      animation: {
        wiggle: 'wiggle 1s ease-in-out infinite',
        'pulse-ring': 'pulse-ring 2s cubic-bezier(0.4, 0, 0.6, 1) infinite',
      },
      colors: {'tastefully-pumpkin': '#DC8665',
               'theom': '#138086',
               'aquarium-rocks': '#16949b',
               'barney-shet': '#554869',
               'tired-peach-pink': '#CD7672',
               'sick-camel': '#EEB462',
               'cherry-blossom-yoghurt': '#F5CDC6',
               'burnt-bubblegum': '#EF9796',
               'peach-eyeshadow': '#FFC98B',
               'peached-out': '#FFB284',
               'introverted-broccoli': '#C6C09b',
               'coral': '#FF7F50',
               'sail-far-blue': '#4fd0ff'}}
  },
  plugins: [
    require('@tailwindcss/forms'),
  ],
}
