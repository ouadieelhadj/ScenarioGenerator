import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.moneycore.merchantportal',
  appName: 'FuturPayment Merchant',
  webDir: 'dist/merchant-mobile/browser',
  loggingBehavior: 'none',
  server: {
    androidScheme: 'https',
    cleartext: false,
  },
};

export default config;
