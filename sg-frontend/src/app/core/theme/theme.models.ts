// Un theme = un ensemble de surcharges de variables CSS.
// La cle correspond au nom de la variable CSS (sans le prefixe --).
export interface ThemeTokens {
  [cssVar: string]: string;
}

export interface Theme {
  id: string;
  label: string;
  dark: boolean;
  tokens: ThemeTokens;
}
