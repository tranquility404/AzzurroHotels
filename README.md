# Azzurro Hotels

I built this prototype to help hotel operations managers explore Booking.com hotel and review data through a dashboard or Azzurro AI Assistant.

## Live

- App: https://azzurrohotels.vercel.app
- API docs: https://azzurrohotels-ab8c937d4e73.herokuapp.com/swagger-ui/index.html
- The backend instance will be shut down on **22 Dec 2026**.

## What it does

- Explore hotel facilities, rooms, rules, and nearby places.
- Review hotel performance, trends, and review topics; search and filter individual reviews.
- Manually sync hotel details and reviews. New review pages are previewed and need approval before import.
- Ask Azzurro AI Assistant questions about hotel operations. It fetches relevant hotel and review context and streams its response and tool activity.

## Built with

Next.js, TypeScript, Spring Boot, Java 21, PostgreSQL, Supabase Storage, Omkar Cloud, and the OpenAI Responses API.

## Run locally

Requirements: JDK 21, Node.js 20.9 or newer, npm, and an initialized PostgreSQL database.

Open two PowerShell terminals from the project folder.

Backend:

```powershell
cd backend
# Set the backend environment variables below in this terminal.
.\gradlew.bat :app:bootRun
```

Frontend:

```powershell
cd frontend
$env:AZZURRO_API_URL = "http://localhost:8080"
npm ci # first run only
npm run dev
```

Set these backend variables before starting the app:

- Database: `DB_USERNAME`, `DB_PASSWORD`; `DB_URL` defaults to `jdbc:postgresql://localhost:5432/postgres`.
- OpenAI: `OPENAI_API_KEY`, `OPENAI_BASE_URL`, `OPENAI_MODEL`.
- Booking.com data provider: `OMKAR_API_KEY`.
- Supabase: `SUPABASE_URL`, `SUPABASE_SERVICE_ROLE_KEY`, `SUPABASE_STORAGE_APP_BUCKET`, `SUPABASE_STORAGE_USER_BUCKET`, `SUPABASE_STORAGE_PUBLIC_ASSETS_BUCKET`, `SUPABASE_STORAGE_TEMP_BUCKET`.
- Optional: `OPENAI_REASONING_EFFORT` (default `low`), `INSIGHTS_LLM_MAX_CALLS` (default `100`), `INSIGHTS_LLM_MAX_PROMPT_TOKENS` (default `20000`), `PORT` (default `8080`).

The migration files are not included in the current source, so a new empty database will not be initialized automatically. Set environment variables in your shell; Spring Boot does not load a plain `.env` file by default.

### Seed local test data

The frontend's Add Hotel page was removed, but the backend collection endpoint remains. It uses Omkar Cloud to fetch hotel details, save the hotel, and archive its snapshot. It accepts up to four URLs per request:

```powershell
$bookingUrl = "https://www.booking.com/hotel/au/your-hotel.html"
$result = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/hotels/collect" `
  -ContentType "application/json" `
  -Body (@{ bookingUrls = @($bookingUrl) } | ConvertTo-Json)
$hotelId = $result.properties[0].hotelId
```

The endpoint does not import reviews. Scrape reviews separately with the browser-use command and the persistent Brave session. It fetches all reviews by default; set `BOOKING_MAX_REVIEWS` to a positive number for a smaller sample.

```powershell
$env:BOOKING_HOTEL_URL = $bookingUrl
$env:BOOKING_REVIEWS_OUTPUT = (Join-Path $PWD "scripts\booking_reviews.json")
# Optional: $env:BOOKING_MAX_REVIEWS = "100"
Get-Content .\scripts\scrape_booking_reviews.py -Raw | browser-use
python .\scripts\reviews_json_to_csv.py $hotelId $env:BOOKING_REVIEWS_OUTPUT
```

Import the generated `scripts/booking_reviews_reviews_import.csv` into the `public.reviews` table in the Supabase project used by `DB_URL` (Table Editor → Import data from CSV). This bulk import avoids sending thousands of individual inserts through the app. Create the hotel first and use a fresh test hotel; existing review rows can conflict with the table's unique constraints.

## Prototype limits

- No authentication; this is a prototype, not a production system for private data.
- AI usage is capped at 100 model calls per backend process and 20,000 prompt tokens per call.
- Review sync is manual and rate-limited to help stay within the Omkar Cloud request allowance.

## Build notes — Nov 21–22, 2026

- Started with Booking.com data collection and scraped about 9,500 reviews.
- Built the hotel explorer, operations dashboard, date-filtered analytics, review search, pagination, and topic classification.
- Added manual review sync with duplicate detection and an approval step for each new page; fixed a review identity bug found during testing.
- Added Azzurro AI Assistant with hotel, review, and current-time tools, streamed responses, JDBC chat memory, and usage limits.

## Demo videos

### Dashboard walkthrough

https://github.com/user-attachments/assets/345bafbd-17ea-4f1c-a8c4-d1bdc213b474

### Azzurro AI Assistant

https://github.com/user-attachments/assets/f46186af-ab8b-4cd7-acf1-2eef9f7e7fa1

### Review sync

https://github.com/user-attachments/assets/a89295af-e645-4a68-b040-6e6d438b054b

## Screenshots

<details>
<summary>Dashboard</summary>

<p>
<img src="demo/dashboard/Screenshot%202026-11-22%20025603.png" width="48%" alt="Dashboard screenshot 1">
<img src="demo/dashboard/Screenshot%202026-11-22%20025613.png" width="48%" alt="Dashboard screenshot 2">
<img src="demo/dashboard/Screenshot%202026-11-22%20025625.png" width="48%" alt="Dashboard screenshot 3">
<img src="demo/dashboard/Screenshot%202026-11-22%20025640.png" width="48%" alt="Dashboard screenshot 4">
<img src="demo/dashboard/Screenshot%202026-11-22%20025332.png" width="48%" alt="Dashboard screenshot 5">
<img src="demo/dashboard/Screenshot%202026-11-22%20025350.png" width="48%" alt="Dashboard screenshot 6">
<img src="demo/dashboard/Screenshot%202026-11-22%20025414.png" width="48%" alt="Dashboard screenshot 7">
<img src="demo/dashboard/Screenshot%202026-11-22%20025431.png" width="48%" alt="Dashboard screenshot 8">
<img src="demo/dashboard/Screenshot%202026-11-22%20025444.png" width="48%" alt="Dashboard screenshot 9">
</p>

</details>

<details>
<summary>Azzurro AI Assistant</summary>

<p>
<img src="demo/ai-assistant/Screenshot%202026-11-22%20025944.png" width="96%" alt="Azzurro AI Assistant screenshot 1">

<img src="demo/ai-assistant/Screenshot%202026-11-22%20030000.png" width="48%" alt="Azzurro AI Assistant screenshot 2">
<img src="demo/ai-assistant/Screenshot%202026-11-22%20030143.png" width="48%" alt="Azzurro AI Assistant screenshot 5">

<img src="demo/ai-assistant/Screenshot%202026-11-22%20030045.png" width="48%" alt="Azzurro AI Assistant screenshot 3">
<img src="demo/ai-assistant/Screenshot%202026-11-22%20030059.png" width="48%" alt="Azzurro AI Assistant screenshot 4">


<img src="demo/ai-assistant/Screenshot%202026-11-22%20030216.png" width="48%" alt="Azzurro AI Assistant screenshot 6">
<img src="demo/ai-assistant/Screenshot%202026-11-22%20030232.png" width="48%" alt="Azzurro AI Assistant screenshot 7">
<img src="demo/ai-assistant/Screenshot%202026-11-22%20030301.png" width="48%" alt="Azzurro AI Assistant screenshot 8">
<img src="demo/ai-assistant/Screenshot%202026-11-22%20030320.png" width="48%" alt="Azzurro AI Assistant screenshot 9">
</p>

</details>
