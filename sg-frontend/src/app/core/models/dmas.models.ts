export interface Card {
  pan: string;
  pin?: string;
  balance: number;      // centimes
  currency: string;     // ex "840"
  expiry?: string;      // AAMM
  status?: string;
}

export interface CardRequest {
  pan: string;
  pin: string;
  balance?: number;
  currency?: string;
  expiry?: string;
}

export interface AuthRequest {
  DE002_PAN: string;
  DE004_AMOUNT: number;
  DE003_PROCESSING_CODE?: string;
  DE018_MCC?: string;
  DE022_POS_ENTRY_MODE?: string;
  DE025_POS_CONDITION_CODE?: string;
  DE032_ACQUIRING_BIN?: string;
  DE041_TERMINAL_ID?: string;
  DE042_MERCHANT_ID?: string;
  DE043_MERCHANT_NAME?: string;
  DE049_CURRENCY_CODE?: string;
  DE052_PIN?: string;
}

