export const formatDateTime = (value: unknown, emptyValue = '-'): string => {
  if (!value) return emptyValue;
  const date = new Date(String(value));
  if (Number.isNaN(date.getTime())) return emptyValue;
  return date.toLocaleString();
};
