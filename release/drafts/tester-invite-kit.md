# Closed-test tester invite kit (DRAFT, do not send)

Source on the rule (checked 2026-11-07): App testing requirements for new personal developer accounts
https://support.google.com/googleplay/android-developer/answer/14151465?hl=en
- Personal accounts created after 13 Nov 2023 must run a **closed test** with **at least 12 testers opted in continuously for the 14 days** before applying for production. Internal testing doesn't count.
- Then use "Apply for production" on the Dashboard (three-part questionnaire). Review "usually takes seven days or less".
- A tester who opts out and rejoins starts a new 14 days. Adding an email to the list isn't enough: the tester has to accept via the opt-in link. **Recruit 15 to 18** for a buffer.

---

## Invite: English
Subject: Can you help test my new app, EventClip? (2 weeks, tiny effort)

Hi [NAME],

I'm getting my Android app **EventClip** ready for Google Play and I need a few friends to test it first.

**What it does:** you pick your own photos and videos, choose a song that's already on your phone, and EventClip automatically cuts them into a birthday movie. Everything happens on your phone, with no account and nothing uploaded.

**What you need:** an Android phone and a Google account (the one you use for Play Store).

**How to join (2 minutes):**
1. Send me the Gmail/Google address you use on your phone, so I can add you to the test list.
2. Open this link on your phone: [OPT_IN_LINK], and tap **"Become a tester"**.
3. Install EventClip from the Play Store link on that page.

**The one important part:** please **stay in the test for at least 14 days** (don't tap "Leave the program"), and open the app a few times. Try making a movie! Google needs 12 people to stay in for 14 days in a row before I can publish.

**Found a bug or something confusing?** Tell me here: [BUG_REPORT_LINK_OR_EMAIL]. A screenshot and your phone model help a lot.

Thank you so much! 🎂
[SENDER_NAME]

---

<div dir="rtl">

## הזמנה: עברית
נושא: עוזרים לי לבדוק את האפליקציה החדשה שלי? (שבועיים, כמעט בלי מאמץ)

היי [NAME],

אני מכין את האפליקציה שלי, **EventClip**, לעלייה ל-Google Play, ולפני כן אני צריך כמה חברים שיבדקו אותה.

**מה היא עושה?** בוחרים תמונות וסרטונים שלכם ושיר ששמור בטלפון, ו-EventClip עורכת מהם סרטון יום הולדת לבד, בקצב של השיר. הכול קורה בטלפון, בלי הרשמה ובלי להעלות כלום.

**מה צריך?** טלפון אנדרואיד וחשבון Google (זה של חנות Play).

**איך מצטרפים? (2 דקות)**
1. שלחו לי את כתובת ה-Gmail שמחוברת לטלפון, כדי שאוסיף אתכם לרשימת הבודקים.
2. פתחו בטלפון את הקישור [OPT_IN_LINK] ולחצו על **"Become a tester"** (להצטרפות כבודקים).
3. התקינו את EventClip מהקישור לחנות Play שמופיע באותו דף.

**הדבר הכי חשוב:** תישארו בתוכנית **לפחות 14 יום** (בלי ללחוץ על "Leave the program"), ותפתחו את האפליקציה כמה פעמים. נסו להכין סרטון! Google דורשת ש-12 בודקים יישארו 14 יום ברצף לפני שמותר לפרסם.

**נתקלתם בבאג או במשהו לא ברור?** ספרו לי כאן: [BUG_REPORT_LINK_OR_EMAIL]. צילום מסך ודגם הטלפון מאוד עוזרים.

תודה ענקית! 🎂
[SENDER_NAME]

</div>

---

## Reminder (day 7 and day 12), EN / HE
- EN: "Quick reminder: please stay in the EventClip test until [END_DATE] and open the app once or twice this week. Thanks!"
- <span dir="rtl">HE: "תזכורת קטנה: נשארים בבדיקה של EventClip עד [END_DATE], ופותחים את האפליקציה פעם-פעמיים השבוע. תודה!"</span>

## Tracking table template
Aim: at least 12 continuously opted in on the planned apply date. The **Play Console opt-in count is the source of truth.**

| # | Name | Google account email | Language | Phone model / Android ver. | Invited (date) | Opted in (date) | Installed? | 14-day mark (opt-in + 14) | Still opted in? (check D7 / D12 / D14) | Bugs reported | Notes |
|---|------|------|------|------|------|------|------|------|------|------|------|
| 1 | | | EN/HE | | | | | | | | |
| 2 | | | | | | | | | | | |
| … up to 18 | | | | | | | | | | | |

Apply-date rule: earliest apply date = the date by which ≥12 testers have each had 14 uninterrupted days.
