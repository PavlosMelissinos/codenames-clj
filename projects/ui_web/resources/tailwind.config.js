module.exports = {
  content: [
    './src/**/*',
  ],
  theme: {
    extend: {
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
