/** @type { import('@storybook/html-webpack5').StorybookConfig } */
module.exports = {
  stories: ['./stories/**/*.stories.js'],
  addons: ['@storybook/addon-essentials'],
  framework: {
    name: '@storybook/html-webpack5',
    options: {},
  },
  docs: { autodocs: false },
};
