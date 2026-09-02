# AdPoint Android

Native Android starter with a real Google Mobile Ads rewarded-ad integration using Google's DEMO rewarded ad unit.

Current reward rules:
- 1 completed rewarded ad = 50 points
- 20 ads/day default cap
- 2,000 points = ₹10 Google Play redeem request

## Build
Open this folder in Android Studio and let Gradle sync.

## Testing
The project uses Google's official demo rewarded ad unit:
ca-app-pub-3940256099942544/5224354917

Do not use live ads while developing. Before release, replace the demo App ID and rewarded ad unit with IDs from your own AdMob account. Google explicitly recommends test ads during development.

## Production requirements
This starter stores points locally for demonstration. For real rewards, move balances, reward validation, daily limits, redemption requests and gift-code fulfillment to a secure server. Award points only from the rewarded-ad earned-reward callback. Add authentication, fraud controls, rate limits, audit logs, and a proper admin panel before enabling real Google Play codes.

Google Play gift cards are country/currency restricted. In India, Google currently lists ₹100, ₹300, ₹500, ₹1,000, ₹1,500 and variable ₹10–₹5,000 gift-card denominations; confirm the exact denomination and distribution method you intend to use before promising ₹10 codes to users.

Final UI update: 50 daily ad limit, functional history, wallet, redeem options ₹30/₹50/₹80/₹159, and functional profile information pages.
