// States and union territories, as a delivery address form offers them. A
// fixed list rather than free text: "TN", "Tamilnadu" and "Tamil Nadu" are one
// state to a courier and three to a database.
export const INDIAN_STATES = [
  'Andaman and Nicobar Islands',
  'Andhra Pradesh',
  'Arunachal Pradesh',
  'Assam',
  'Bihar',
  'Chandigarh',
  'Chhattisgarh',
  'Dadra and Nagar Haveli and Daman and Diu',
  'Delhi',
  'Goa',
  'Gujarat',
  'Haryana',
  'Himachal Pradesh',
  'Jammu and Kashmir',
  'Jharkhand',
  'Karnataka',
  'Kerala',
  'Ladakh',
  'Lakshadweep',
  'Madhya Pradesh',
  'Maharashtra',
  'Manipur',
  'Meghalaya',
  'Mizoram',
  'Nagaland',
  'Odisha',
  'Puducherry',
  'Punjab',
  'Rajasthan',
  'Sikkim',
  'Tamil Nadu',
  'Telangana',
  'Tripura',
  'Uttar Pradesh',
  'Uttarakhand',
  'West Bengal',
];

/**
 * Maps a state name from a lookup service onto this list. The PIN code service
 * and OpenStreetMap spell some names differently ("NCT of Delhi", "Orissa").
 */
export function matchState(name) {
  if (!name) return '';
  const cleaned = name.toLowerCase().replace(/[^a-z]/g, '');
  const aliases = { nctofdelhi: 'Delhi', orissa: 'Odisha', pondicherry: 'Puducherry' };
  if (aliases[cleaned]) return aliases[cleaned];
  return INDIAN_STATES.find((state) => state.toLowerCase().replace(/[^a-z]/g, '') === cleaned) ?? '';
}
