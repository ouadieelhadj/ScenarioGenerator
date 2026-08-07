import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.moneycore.merchantportal',
  appName: 'FuturPayment Merchant',
  webDir: 'dist/merchant-mobile/browser',
  server: {
    androidScheme: 'https',
    cleartext: true,
  },
};

export default config;
