const paths = {
  overview: 'M3 10 12 3l9 7v10a1 1 0 0 1-1 1h-5v-7H9v7H4a1 1 0 0 1-1-1Z',
  transfer: 'M4 7h16m-5-5 5 5-5 5M20 17H4m5-5-5 5 5 5',
  history: 'M4 5v5h5M4.5 10a8 8 0 1 1 .5 7M12 7v5l3 2',
  document: 'M6 3h8l4 4v14H6Zm8 0v5h4M9 12h6m-6 4h6',
  info: 'M12 11v6m0-10v.01M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0Z',
} as const;

export function Icon({ name }: { name: keyof typeof paths }) {
  return <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d={paths[name]} /></svg>;
}
