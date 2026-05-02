/**
 * KST calendar date YYYY-MM-DD for sync-once-per-day rules.
 */
export function kstCalendarDateString(now: Date = new Date()): string {
    const kst = new Date(now.getTime() + 9 * 60 * 60 * 1000);
    return kst.toISOString().slice(0, 10);
}
