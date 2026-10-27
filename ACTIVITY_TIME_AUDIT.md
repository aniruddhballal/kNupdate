# Activity time audit

Audited on 27 October 2026 against all 100 activities and their fixed detail choices in `ActivityCatalog.kt`.

## What a time means in this app

This is an activity broadcast app. A person can report a moment, a current situation, or an interval they experienced. It is not exclusively a completed-session tracker.

- **Live:** a single as-of timestamp captured when kNupdate is pressed. It stays available for every activity. It does not invent a start, end, or elapsed duration.
- **Custom At:** an event occurred, or the selected activity/state was reported at that instant. For ongoing activities this does not establish when the whole activity started or finished.
- **Custom From–to:** the user reports that the activity occupied an explicitly selected interval. Both endpoints are required. It does not imply that the entire activity ended at To; someone could report one hour of a longer work session.
- Ordinary custom values must be in the past, and To must be after From. Coordinate snapshots alone can schedule a future At time; the phone requests a fresh fix when due.

The distinction is the meaning of the activity and its details, not how many seconds an action physically takes. A pickup can take several minutes, but this catalog's Collect entry reports the pickup point. Queue or a service/errand activity can describe time spent waiting or visiting. Conversely, a short activity such as drinking coffee can still have a meaningful interval.

## Decisions

| Custom policy | Count | Initial choice inside Custom |
| --- | ---: | --- |
| At only | 8 | At |
| At or From–to | 92 | At for Drink; From–to for the other 91 |
| From–to only | 0 | No current activity warrants this restriction |

At-only: **Location, Wake up, Arrived, Departed, Collect, Delivery, Medicine, Check-in**. Each is a snapshot, transition, handover, dose, or check-in report under its current name and detail choices.

No ongoing activity is forced to From–to. A statement such as “working at 10,” “sleeping at 11,” or “attending therapy at 3” is meaningful even if the person does not know the full interval. Requiring two endpoints would block those legitimate custom snapshots and be inconsistent with the universal Live option. The policy model can represent From–to only, but no existing activity is assigned it merely to fill that category.

Policies cover every existing detail under an activity. Broad activities with mixed uses retain both modes: Drink includes a quick drink and coffee over a break; Post office includes collection and a service visit; Fuel / charge includes a fuel stop and an EV charging session. Changing a detail must not silently change what an already selected timestamp means.

## Everyday

| Activity (`id`) | Meaning of At | Meaning of From–to, or reason it is unavailable | Custom policy / initial choice |
| --- | --- | --- | --- |
| Location (`location`) | The reported place at one instant: Home, Work, School, University, Gym, Outside, or Away. Coordinates are a fix with their own capture time. | Unavailable. A current-location snapshot does not establish residence/occupancy over an interval or a movement trace. One coordinate fix cannot represent a whole range. | At only / At |
| Wake up (`waking`) | The wake-up transition at Home, Away, or after a nap. | Unavailable. This records the wake-up point; getting ready and other routines have their own activities. It does not measure a process of gradually waking. | At only / At |
| Sleep (`sleeping`) | Reported asleep at that moment, for a night's sleep or a nap, at Home or Away. | A reported sleeping interval. A point report cannot supply a missing bedtime or wake time, so neither endpoint is inferred. | Both / From–to |
| Rest (`resting`) | Resting at home, lying down, or taking a short break at that instant. | Time spent resting or taking the break. | Both / From–to |
| Eat (`eating`) | Breakfast, lunch, dinner, a snack, or brunch at that moment. | Time spent eating that meal or snack. Meal type does not make a duration meaningless. | Both / From–to |
| Drink (`drinking`) | A quick drink or a point report of having Water, Coffee, Tea, Juice, Smoothie, or Milk. | A drinking occasion, such as coffee over a break. Restricting the entire activity to a moment would exclude a meaningful use of several details. | Both / At |
| Cook (`cooking`) | Preparing Breakfast, Lunch, Dinner, Baking, or a Snack at that moment. | A cooking or baking interval. This is when cooking happened, not when the meal was planned to be served. | Both / From–to |
| Bathe (`showering`) | In a shower or bath at that moment. | Time spent showering or bathing. | Both / From–to |
| Groom (`grooming`) | Brushing teeth, skincare, shaving, or hair care at that moment. | Time spent on that grooming task or routine. | Both / From–to |
| Get ready (`getting-ready`) | Getting ready for work, school, going out, or bed at that moment. | The preparation period. It does not assert that the subsequent work, outing, or sleep occurred during that range. | Both / From–to |

## Work

| Activity (`id`) | Meaning of At | Meaning of From–to | Custom policy / initial choice |
| --- | --- | --- | --- |
| Work (`working`) | Working at the Office, Home, a Coworking space, or On site at that instant. | A reported work interval, including part of a longer workday. | Both / From–to |
| Meeting (`meeting`) | In a Team meeting, One-to-one, Client meeting, or Workshop at that moment. | Attendance or participation during a selected interval. | Both / From–to |
| Video call (`video-call`) | On a work, family, friends, or appointment call at that instant. | Time spent on that video call. | Both / From–to |
| Phone call (`phone-call`) | On the selected kind of phone call at that instant. | Time spent on that phone call. | Both / From–to |
| Email (`email`) | Reading, Writing, or Clearing inbox at that moment. | An email-handling session. At is a status report, not proof that a message was sent. | Both / From–to |
| Plan (`planning`) | Planning a Day, Week, Project, or Schedule at that moment. | Time spent planning. The time fields describe the planning work, not the future day or project being planned. | Both / From–to |
| Focus (`focus`) | Engaged in Writing, Coding, Design, Analysis, or Problem solving at that instant. | A focused-work interval. No timer or minimum session length is implied. | Both / From–to |
| Present (`presenting`) | Giving a Presentation, Demo, Talk, or Teaching at that moment. | Time spent presenting or teaching. | Both / From–to |
| Interview (`interview`) | In a Job interview, Interviewing, or a Practice interview at that instant. | Participation during an interview or practice interval. | Both / From–to |
| Break (`work-break`) | On a Coffee, Lunch, Screen, or Walk break at that moment. | The break interval. At does not infer when the person stopped working. | Both / From–to |

## Learning

| Activity (`id`) | Meaning of At | Meaning of From–to | Custom policy / initial choice |
| --- | --- | --- | --- |
| Study (`studying`) | Studying at Home, Library, School, University, or with a Study group at that moment. | A study interval, regardless of location. | Both / From–to |
| Read (`reading`) | Reading a Book, Article, News, Comics, or listening to an Audiobook at that instant. | A reading/listening interval, rather than the entire time required to finish a work. | Both / From–to |
| Class (`class`) | Participating In person, Online, in a Lab, or a Tutorial at that moment. | A period of class attendance or participation. | Both / From–to |
| Lecture (`lecture`) | Attending or watching an In person, Online, or Recorded lecture at that instant. | Time spent attending or watching it. | Both / From–to |
| Homework (`homework`) | Working on an Assignment, Project, Problem set, or Lab report at that moment. | Time spent working on that task, without claiming the assignment was completed. | Both / From–to |
| Revision (`revision`) | Reviewing Notes, Flashcards, Practice questions, or Past papers at that instant. | A revision session. | Both / From–to |
| Exam (`exam`) | Taking a Written, Online, Practical exam, or Quiz at that moment. | Time spent taking that assessment. This is a past activity report, not a future exam booking. | Both / From–to |
| Research (`research`) | Reading papers, performing Experiments, Data analysis, or Fieldwork at that instant. | Time spent on the selected research work, not the lifetime of a research project. | Both / From–to |
| Practice (`practice`) | Practising a Language, Instrument, Skill, Driving, or Public speaking at that moment. | A practice interval. | Both / From–to |
| Course (`course`) | Engaged in an Online lesson, Workshop, Training, or Certification activity at that instant. | A period of participation. At does not imply a qualification was awarded; From–to need not cover the entire course. | Both / From–to |

## Movement

| Activity (`id`) | Meaning of At | Meaning of From–to | Custom policy / initial choice |
| --- | --- | --- | --- |
| Exercise (`exercising`) | Exercising at a Gym, Outside, Home, or in a Workout class at that moment. | A workout or exercise interval. | Both / From–to |
| Run (`run`) | Running at that instant, with the selected 5k, 10k, Short run, Long run, Treadmill, or Trail run context. | A reported running interval. Selecting 5k does not make the timestamp a verified start, finish, distance, or pace measurement. | Both / From–to |
| Walk (`walk`) | Walking Outside, in a Park, With a pet, With company, or on a Treadmill at that instant. | A walking interval. | Both / From–to |
| Cycle (`cycle`) | Cycling on Road, Trail, Indoor, or a Commute at that moment. | Time spent cycling. | Both / From–to |
| Gym (`gym`) | Doing Strength, Cardio, Full body, Upper body, or Lower body work at that instant. | A gym session or part of one. | Both / From–to |
| Yoga (`yoga`) | Practising yoga at Home, in Class, or Outside at that moment. | A yoga interval. | Both / From–to |
| Stretch (`stretch`) | Doing a Warm-up, Cool-down, Mobility exercise, or Desk break at that instant. | A stretching/mobility interval, even if brief. | Both / From–to |
| Swim (`swim`) | Swimming in a Pool, Open water, or Lesson at that moment. | A swimming interval. | Both / From–to |
| Hike (`hike`) | Hiking on a Trail, Hill, Mountain, or Nature walk at that instant. | A hiking interval, without inferring the full route or trip duration. | Both / From–to |
| Sport (`sport`) | Participating in the selected sport, Dance, Martial arts, or Skating at that moment. | Time spent participating or practising. All supplied details support both observations. | Both / From–to |

## Travel

| Activity (`id`) | Meaning of At | Meaning of From–to, or reason it is unavailable | Custom policy / initial choice |
| --- | --- | --- | --- |
| Travel (`travelling`) | Travelling by Walking, Driving, Public transport, Taxi, or as a Passenger at that moment. | A reported travelling interval. It does not supply a route or distance. | Both / From–to |
| Commute (`commute`) | On the selected commute To/From work, school, or university at that instant. | A commuting interval. Destination context is separate from time shape. | Both / From–to |
| Drive (`drive`) | Driving a Car, Motorcycle, Scooter, or on a Road trip at that instant. | Time spent driving, possibly one leg of a trip. | Both / From–to |
| Ride (`ride`) | Riding in a Taxi, Rideshare, Auto-rickshaw, or as a Passenger at that moment. | A ride interval. | Both / From–to |
| Bus (`bus`) | On a Local bus, Intercity bus, or Shuttle at that instant. | A bus-journey interval. | Both / From–to |
| Train (`train`) | On a Metro, Local train, Intercity train, or Tram at that moment. | A rail-journey interval. | Both / From–to |
| Flight (`flight`) | On a Domestic, International, or Connecting flight at that instant. | A flight/travel interval. Neither mode infers take-off or landing from a mere point report. | Both / From–to |
| Arrived (`arrived`) | Arrival at Home, Work, School, University, Gym, Station, Airport, or Destination. | Unavailable. Arrival is a transition; a range would ambiguously mean a journey, an arrival window, or a subsequent stay. Travel and Location are separate activities. | At only / At |
| Departed (`departed`) | Departure from the selected place. | Unavailable. Departure is a transition; its journey duration belongs under a travel activity. | At only / At |
| Wait (`waiting`) | Waiting For transport, For someone, At a station, or At an airport at that instant. | A waiting interval. A snapshot does not assert when waiting began or ended. | Both / From–to |

## Leisure

| Activity (`id`) | Meaning of At | Meaning of From–to | Custom policy / initial choice |
| --- | --- | --- | --- |
| Music (`music`) | Listening, Playing, Singing, or attending a Concert at that moment. | Time spent in the selected musical activity. Mixed details make a single mandatory time shape inappropriate. | Both / From–to |
| Watch (`watching`) | Watching a Movie, Series, Video, Documentary, or Live sport at that instant. | A viewing interval, possibly part of a longer programme or event. | Both / From–to |
| Game (`gaming`) | Playing on Console, PC, Mobile, or a Board/Card game at that moment. | A gaming interval. | Both / From–to |
| Create (`creating`) | Drawing, Painting, doing Crafts, Pottery, Sewing, or Design at that instant. | Time spent creating. It does not claim the work was finished. | Both / From–to |
| Write (`writing`) | Writing a Story, Poetry, Blog, or Personal writing at that moment. | A writing session. | Both / From–to |
| Photos (`photography`) | Taking photos, Filming, Editing photos, or Editing video at that instant. | A shooting or editing interval. A whole activity with editing details cannot reasonably be restricted to a shutter-press event. | Both / From–to |
| Garden (`gardening`) | Watering, Planting, Pruning, or doing Plant care at that moment. | Time spent gardening. | Both / From–to |
| Explore (`exploring`) | Exploring a City, Nature, Museum, or Sightseeing at that instant. | A period of exploration or visiting. | Both / From–to |
| Event (`event`) | Attending Cinema, Theatre, a Festival, Exhibition, or Live show at that moment. | An attendance interval, rather than necessarily the event's official opening/closing times. | Both / From–to |
| Relax (`relaxing`) | Relaxing At home, Outside, during Quiet time, or Screen time at that instant. | A relaxation interval. | Both / From–to |

## Social

| Activity (`id`) | Meaning of At | Meaning of From–to | Custom policy / initial choice |
| --- | --- | --- | --- |
| Meet (`meeting-people`) | In a Catch-up, meeting New people, or a Group gathering at that moment. | Time spent meeting or socialising. This differs from the point event Arrived. | Both / From–to |
| Family (`family`) | Spending family time At home, on an Outing, or during a Meal together at that instant. | A family-time interval. | Both / From–to |
| Friends (`friends`) | Hanging out, having a Meal together, or on an Outing with friends at that moment. | Time spent together. | Both / From–to |
| Date (`date`) | On a date involving a Meal, Coffee, Walk, or Outing at that instant. | Time spent on that occasion. The activity name is not a calendar-date input. | Both / From–to |
| Visit (`visiting`) | Visiting Family, Friends, or Neighbours at that moment. | A visiting interval. | Both / From–to |
| Chat (`chatting`) | Chatting In person, Online, or in a Group chat at that instant. | A conversation interval, including part of a longer conversation. | Both / From–to |
| Celebrate (`celebrating`) | Celebrating a Birthday, Wedding, Anniversary, Festival, or Achievement at that moment. | Time spent celebrating. A wedding/birthday detail does not turn participation into a single ceremonial instant. | Both / From–to |
| Volunteer (`volunteering`) | Volunteering in Community, Environment, Food, Animals, or Education work at that instant. | A volunteering interval or shift. | Both / From–to |
| Pet time (`pet-time`) | Playing, Cuddling, Training, or spending time Outside with a pet at that moment. | Time spent together. | Both / From–to |
| Community (`community`) | Participating in a Club, Meetup, Group activity, or Local gathering at that instant. | A participation interval, not the duration of membership in a group. | Both / From–to |

## Home

| Activity (`id`) | Meaning of At | Meaning of From–to | Custom policy / initial choice |
| --- | --- | --- | --- |
| Clean (`cleaning`) | Cleaning Home, Kitchen, Bathroom, Bedroom, or Car at that moment. | A cleaning interval. | Both / From–to |
| Laundry (`laundry`) | Washing, Drying, Folding, or Ironing at that instant. | The reported laundry phase/session. It need not mean continuous hands-on work or the entire machine cycle. | Both / From–to |
| Dishes (`dishes`) | Washing up, Loading dishwasher, or Putting away at that moment. | Time spent on that task. | Both / From–to |
| Tidy (`tidying`) | Tidying a Room, Desk, Wardrobe, or Decluttering at that instant. | A tidying interval. | Both / From–to |
| Repair (`maintenance`) | Doing Home repair, DIY, Vehicle, or Electronics maintenance at that moment. | Time spent on that work, without claiming the repair was completed. | Both / From–to |
| Move home (`moving-home`) | Packing, Unpacking, or Moving furniture at that instant. | Time spent on a moving task, not necessarily the whole house move. | Both / From–to |
| Childcare (`childcare`) | Playing, Feeding, on a School run, handling Bedtime, or Caring at that moment. | A care or task interval. Bedtime/feeding details do not require reducing the entire broad activity to an event. | Both / From–to |
| Pet care (`pet-care`) | Feeding, Grooming, Cleaning, or at a Vet visit at that instant. | Time spent on care or a visit. Grooming and visits make a duration useful even when feeding is logged as a point. | Both / From–to |
| Meal prep (`meal-prep`) | Batch cooking, Packing lunch, Chopping, or Meal planning at that moment. | A preparation/planning interval. The selected time is when preparation happened, not the planned meal time. | Both / From–to |
| Home admin (`home-admin`) | Handling Bills, Budget, Paperwork, Scheduling, or Organising files at that instant. | An admin session. A quick bill-related action and a longer paperwork session both fit this category. | Both / From–to |

## Errands

| Activity (`id`) | Meaning of At | Meaning of From–to, or reason it is unavailable | Custom policy / initial choice |
| --- | --- | --- | --- |
| Shop (`shopping`) | Shopping for Clothes, Household items, Electronics, Gifts, or Online at that moment. | A shopping/browsing interval. Online shopping may be brief or extended. | Both / From–to |
| Groceries (`groceries`) | Shopping at a Supermarket, Market, Local shop, or placing an Online order at that instant. | Time spent grocery shopping or assembling the order. | Both / From–to |
| Collect (`collecting`) | The Parcel, Order, Food, or Documents pickup/collection point. | Unavailable. These details describe a specific collection/handover, not a collection round or time spent queuing. Queue, Shop, or Post office can report an interval of effort or attendance. | At only / At |
| Delivery (`delivery`) | The Parcel, Food, Documents, or Drop-off handover point, whether reporting receipt or drop-off. | Unavailable. This catalog entry does not describe a courier shift, delivery route, or a promised delivery window. Those would need a differently specified activity. | At only / At |
| Appointment (`appointment`) | Attending a Personal, Professional, Service, or Consultation appointment at that instant. | Time spent attending. An appointment can occupy a session, so it is not restricted like Arrived. Ordinary future bookings are not introduced by this policy. | Both / From–to |
| Bank (`bank`) | At a Bank branch, using an ATM, or doing Online banking at that moment. | A banking/branch-service session. A quick ATM transaction and a longer online session both remain expressible. | Both / From–to |
| Post office (`post-office`) | Posting, Collecting, or using a Courier service at that instant. | A visit/service interval. The activity spans more than the specific Collect handover entry. | Both / From–to |
| Fuel / charge (`fuel-charge`) | At a Fuel stop, EV charging, or Vehicle charging at that moment. | A refuelling/charging interval, particularly important for longer charging sessions. | Both / From–to |
| Queue (`queue`) | Queuing at a Shop, Service, for Tickets, or at an Office at that instant. | A queuing interval. Neither endpoint is inferred from an isolated status report. | Both / From–to |
| Haircut (`haircut`) | Getting a haircut at a Barber, Salon, or At home at that moment. | The haircut/service interval. | Both / From–to |

## Wellbeing

| Activity (`id`) | Meaning of At | Meaning of From–to, or reason it is unavailable | Custom policy / initial choice |
| --- | --- | --- | --- |
| Meditate (`meditating`) | Doing Guided, Quiet, or Mindfulness meditation at that instant. | A meditation interval. A status report need not know the start of the session. | Both / From–to |
| Breathe (`breathing`) | Doing a Breathing exercise or taking a Quiet pause at that moment. | An exercise/pause interval. This records the supplied activity details, not ordinary breathing throughout life. | Both / From–to |
| Journal (`journaling`) | A Reflection, Gratitude, or Daily journal entry/status at that instant. | A journaling/writing session. Both a brief entry and an extended session are meaningful. | Both / From–to |
| Pray (`praying`) | Praying At home, at a Place of worship, or in Quiet reflection at that moment. | A prayer/reflection interval, without imposing a particular ritual duration. | Both / From–to |
| Health visit (`health-visit`) | At a Doctor, Dentist, Check-up, Test, or Pharmacy visit at that instant. | A visit/consultation interval. Pharmacy and testing details may be brief; others may occupy a session. No clinical conclusions follow from either mode. | Both / From–to |
| Therapy (`therapy`) | Attending a Session, Physiotherapy, or Counselling at that moment. | A therapy-session interval. At remains valid as an attendance snapshot; it is not a treatment-duration recommendation. | Both / From–to |
| Self-care (`self-care`) | In Quiet time, Personal care, or a Digital break at that instant. | A self-care interval. | Both / From–to |
| Recover (`recovering`) | Reporting a Rest day, recovery At home, or recovery After exercise at that moment. | A reported recovery/rest interval. It does not diagnose recovery, predict its completion, or infer a medical course. | Both / From–to |
| Medicine (`medicine`) | A Scheduled dose, Vitamins, or Supplements intake report at a single time. | Unavailable. A range could imply a course, dose window, or infusion, none of which is specified by these fixed details. This is not a medication schedule or clinical record. | At only / At |
| Check-in (`check-in`) | Reporting Feeling good, Feeling okay, Taking it easy, or Need a break at that instant. | Unavailable. A check-in timestamps a report; it cannot establish when a feeling started or how long it lasted. | At only / At |

## Implementation and compatibility

- `ActivityOption` requires an explicit `ActivityTimePolicy` for every entry. The custom default must be one of the supported modes.
- The Custom editor shows a selector only when both modes are supported. Single-mode activities show their mode and the appropriate fields. Restored editor drafts are constrained to the current policy.
- `ActivityTime.selectionError` is shared by the editor and the final send-button handler. A stale or restored range for an At-only activity is rejected before storing or posting a new activity. A partial custom range is not silently turned into Live.
- Live always stays an instant, including for the policy model's currently unused From–to-only case. Custom capabilities describe explicit historical input; they do not change Live semantics.
- Existing history is retained exactly as recorded, including older ranges for activities now restricted to At. Incoming records from older clients remain readable. No history rewriting, database migration, protocol field, or Cloudflare deployment is needed.
- This is a local composer policy, not an authentication or relay-content guarantee. The public relay still accepts its generic validated activity schema and does not receive a catalog activity ID.
- The existing date-picker limitations and future-coordinate scheduling rules are unchanged. This audit does not add recurring timers, attendance tracking, duration measurement, medical guidance, or automatic start/end inference.

## Verification

Catalog tests lock the eight At-only decisions, require supported defaults, retain all 100 distinct activities/icons, and cover mixed-use details. Time-selection tests reject stale forbidden modes, missing range fields, and invalid times while preserving Live for every catalog entry. Phone UI tests cover all eight At-only entries, both-mode editing, Drink's At default with range support, cancellation, recreation, and switching back to Live. Device UI tests edit drafts without publishing activities.

Release validation for 1.3.5 passed: unit tests, debug and preview lint, the optimized APK build, and all five phone UI tests. An additional SQLite/wire regression test confirmed that a Location range from an older client still loads with its exact original times, even though the new composer rejects that shape. The opt-in real-relay round-trip test was skipped; no backend or wire-schema change was made. A separate coverage check matched all 100 audit rows, policies, and custom defaults to the catalog.
