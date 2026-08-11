export const MERCHANT_ACTIVATION_ORIGIN = 'https://portal.futurpayment.com';
export const MERCHANT_ACTIVATION_PATH = '/activation';

export function merchantActivationUrl(token: string): string {
  return `${MERCHANT_ACTIVATION_ORIGIN}${MERCHANT_ACTIVATION_PATH}?token=${encodeURIComponent(token)}`;
}
