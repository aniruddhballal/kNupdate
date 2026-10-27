package com.knupdate.app

import com.knupdate.app.ActivityTimePolicy.AT_ONLY
import com.knupdate.app.ActivityTimePolicy.BOTH

/** Custom-time capabilities; Live is always a single as-of timestamp. */
internal enum class ActivityTimePolicy(val allowsAt: Boolean, val allowsRange: Boolean) {
    AT_ONLY(true, false),
    RANGE_ONLY(false, true),
    BOTH(true, true);

    fun allows(range: Boolean): Boolean = if (range) allowsRange else allowsAt

    fun rangeSelection(preferred: Boolean): Boolean = when (this) {
        AT_ONLY -> false
        RANGE_ONLY -> true
        BOTH -> preferred
    }
}

internal data class ActivityOption(
    val id: String,
    val title: String,
    val label: String,
    val icon: Int,
    val bodies: List<String>,
    val timePolicy: ActivityTimePolicy,
    val defaultIsRange: Boolean
) {
    init { require(timePolicy.allows(defaultIsRange)) { "Custom default must be supported by the activity." } }
}

internal data class ActivityGroup(val id: String, val label: String, val icon: Int, val activities: List<ActivityOption>)

private fun activity(id: String, title: String, icon: Int, vararg details: String,
    timePolicy: ActivityTimePolicy, defaultIsRange: Boolean = timePolicy != AT_ONLY,
    label: String = title) = ActivityOption(id, title, label, icon, details.toList(), timePolicy, defaultIsRange)

// Every activity has an audited, explicit custom-time policy. See ACTIVITY_TIME_AUDIT.md.
// A fixed, standalone catalog. No user-created activities or remote catalog dependency.
internal val ACTIVITY_GROUPS = listOf(
    ActivityGroup("everyday", "Everyday", R.drawable.ic_activity_location, listOf(
        activity("location", "Current location", R.drawable.ic_activity_location, "Home", "Work", "School", "University", "Gym", "Outside", "Away", timePolicy = AT_ONLY, label = "Location"),
        activity("waking", "Waking up", R.drawable.ic_activity_waking, "Home", "Away", "After a nap", timePolicy = AT_ONLY, label = "Wake up"),
        activity("sleeping", "Sleeping", R.drawable.ic_activity_sleeping, "Night's sleep", "Nap", "Home", "Away", timePolicy = BOTH, label = "Sleep"),
        activity("resting", "Resting", R.drawable.ic_activity_resting, "At home", "Short break", "Lying down", timePolicy = BOTH, label = "Rest"),
        activity("eating", "Eating", R.drawable.ic_activity_eating, "Breakfast", "Lunch", "Dinner", "Snack", "Brunch", timePolicy = BOTH, label = "Eat"),
        activity("drinking", "Drinking", R.drawable.ic_activity_drinking, "Water", "Coffee", "Tea", "Juice", "Smoothie", "Milk", timePolicy = BOTH, defaultIsRange = false, label = "Drink"),
        activity("cooking", "Cooking", R.drawable.ic_activity_cooking, "Breakfast", "Lunch", "Dinner", "Baking", "Snack", timePolicy = BOTH, label = "Cook"),
        activity("showering", "Bathing", R.drawable.ic_activity_showering, "Shower", "Bath", timePolicy = BOTH, label = "Bathe"),
        activity("grooming", "Grooming", R.drawable.ic_activity_grooming, "Brushing teeth", "Skincare", "Shaving", "Hair care", timePolicy = BOTH, label = "Groom"),
        activity("getting-ready", "Getting ready", R.drawable.ic_activity_getting_ready, "For work", "For school", "To go out", "For bed", timePolicy = BOTH, label = "Get ready")
    )),
    ActivityGroup("work", "Work", R.drawable.ic_activity_working, listOf(
        activity("working", "Working", R.drawable.ic_activity_working, "Office", "Home", "Coworking space", "On site", timePolicy = BOTH, label = "Work"),
        activity("meeting", "Meeting", R.drawable.ic_activity_meeting, "Team meeting", "One-to-one", "Client meeting", "Workshop", timePolicy = BOTH),
        activity("video-call", "Video call", R.drawable.ic_activity_video_call, "Work", "Family", "Friends", "Appointment", timePolicy = BOTH),
        activity("phone-call", "Phone call", R.drawable.ic_activity_phone_call, "Work", "Family", "Friends", "Appointment", timePolicy = BOTH),
        activity("email", "Email", R.drawable.ic_activity_email, "Reading", "Writing", "Clearing inbox", timePolicy = BOTH),
        activity("planning", "Planning", R.drawable.ic_activity_planning, "Day", "Week", "Project", "Schedule", timePolicy = BOTH, label = "Plan"),
        activity("focus", "Focused work", R.drawable.ic_activity_focus, "Writing", "Coding", "Design", "Analysis", "Problem solving", timePolicy = BOTH, label = "Focus"),
        activity("presenting", "Presenting", R.drawable.ic_activity_presenting, "Presentation", "Demo", "Talk", "Teaching", timePolicy = BOTH, label = "Present"),
        activity("interview", "Interview", R.drawable.ic_activity_interview, "Job interview", "Interviewing", "Practice interview", timePolicy = BOTH),
        activity("work-break", "Work break", R.drawable.ic_activity_work_break, "Coffee break", "Lunch break", "Screen break", "Walk break", timePolicy = BOTH, label = "Break")
    )),
    ActivityGroup("learning", "Learning", R.drawable.ic_activity_studying, listOf(
        activity("studying", "Studying", R.drawable.ic_activity_studying, "Home", "Library", "School", "University", "Study group", timePolicy = BOTH, label = "Study"),
        activity("reading", "Reading", R.drawable.ic_activity_reading, "Book", "Article", "News", "Comics", "Audiobook", timePolicy = BOTH, label = "Read"),
        activity("class", "Class", R.drawable.ic_activity_class, "In person", "Online", "Lab", "Tutorial", timePolicy = BOTH),
        activity("lecture", "Lecture", R.drawable.ic_activity_lecture, "In person", "Online", "Recorded", timePolicy = BOTH),
        activity("homework", "Homework", R.drawable.ic_activity_homework, "Assignment", "Project", "Problem set", "Lab report", timePolicy = BOTH),
        activity("revision", "Revision", R.drawable.ic_activity_revision, "Notes", "Flashcards", "Practice questions", "Past papers", timePolicy = BOTH),
        activity("exam", "Exam", R.drawable.ic_activity_exam, "Written exam", "Online exam", "Practical", "Quiz", timePolicy = BOTH),
        activity("research", "Research", R.drawable.ic_activity_research, "Reading papers", "Experiments", "Data analysis", "Fieldwork", timePolicy = BOTH),
        activity("practice", "Practice", R.drawable.ic_activity_practice, "Language", "Instrument", "Skill", "Driving", "Public speaking", timePolicy = BOTH),
        activity("course", "Course", R.drawable.ic_activity_course, "Online lesson", "Workshop", "Training", "Certification", timePolicy = BOTH)
    )),
    ActivityGroup("movement", "Movement", R.drawable.ic_activity_run, listOf(
        activity("exercising", "Exercising", R.drawable.ic_activity_exercising, "Gym", "Outside", "Home", "Workout class", timePolicy = BOTH, label = "Exercise"),
        activity("run", "Run", R.drawable.ic_activity_run, "5k", "10k", "Short run", "Long run", "Treadmill", "Trail run", timePolicy = BOTH),
        activity("walk", "Walk", R.drawable.ic_activity_walk, "Outside", "Park", "With a pet", "With company", "Treadmill", timePolicy = BOTH),
        activity("cycle", "Cycle", R.drawable.ic_activity_cycle, "Road", "Trail", "Indoor", "Commute", timePolicy = BOTH),
        activity("gym", "Gym", R.drawable.ic_activity_gym, "Strength", "Cardio", "Full body", "Upper body", "Lower body", timePolicy = BOTH),
        activity("yoga", "Yoga", R.drawable.ic_activity_yoga, "Home", "Class", "Outside", timePolicy = BOTH),
        activity("stretch", "Stretching", R.drawable.ic_activity_stretch, "Warm-up", "Cool-down", "Mobility", "Desk break", timePolicy = BOTH, label = "Stretch"),
        activity("swim", "Swim", R.drawable.ic_activity_swim, "Pool", "Open water", "Lesson", timePolicy = BOTH),
        activity("hike", "Hike", R.drawable.ic_activity_hike, "Trail", "Hill", "Mountain", "Nature walk", timePolicy = BOTH),
        activity("sport", "Sport", R.drawable.ic_activity_sport, "Football", "Cricket", "Basketball", "Badminton", "Tennis", "Table tennis", "Volleyball", "Dance", "Martial arts", "Skating", timePolicy = BOTH)
    )),
    ActivityGroup("travel", "Travel", R.drawable.ic_activity_travelling, listOf(
        activity("travelling", "Travelling", R.drawable.ic_activity_travelling, "Walking", "Driving", "Public transport", "Taxi", "Passenger", timePolicy = BOTH, label = "Travel"),
        activity("commute", "Commute", R.drawable.ic_activity_commute, "To work", "From work", "To school", "From school", "To university", "From university", timePolicy = BOTH),
        activity("drive", "Drive", R.drawable.ic_activity_drive, "Car", "Motorcycle", "Scooter", "Road trip", timePolicy = BOTH),
        activity("ride", "Ride", R.drawable.ic_activity_ride, "Taxi", "Rideshare", "Auto-rickshaw", "Passenger", timePolicy = BOTH),
        activity("bus", "Bus", R.drawable.ic_activity_bus, "Local bus", "Intercity bus", "Shuttle", timePolicy = BOTH),
        activity("train", "Train", R.drawable.ic_activity_train, "Metro", "Local train", "Intercity train", "Tram", timePolicy = BOTH),
        activity("flight", "Flight", R.drawable.ic_activity_flight, "Domestic", "International", "Connecting flight", timePolicy = BOTH),
        activity("arrived", "Arrived", R.drawable.ic_activity_arrived, "Home", "Work", "School", "University", "Gym", "Station", "Airport", "Destination", timePolicy = AT_ONLY),
        activity("departed", "Departed", R.drawable.ic_activity_departed, "Home", "Work", "School", "University", "Gym", "Station", "Airport", "Destination", timePolicy = AT_ONLY),
        activity("waiting", "Waiting", R.drawable.ic_activity_waiting, "For transport", "For someone", "At a station", "At an airport", timePolicy = BOTH, label = "Wait")
    )),
    ActivityGroup("leisure", "Leisure", R.drawable.ic_activity_music, listOf(
        activity("music", "Music", R.drawable.ic_activity_music, "Listening", "Playing", "Singing", "Concert", timePolicy = BOTH),
        activity("watching", "Watching", R.drawable.ic_activity_watching, "Movie", "Series", "Video", "Documentary", "Live sport", timePolicy = BOTH, label = "Watch"),
        activity("gaming", "Gaming", R.drawable.ic_activity_gaming, "Console", "PC", "Mobile", "Board game", "Card game", timePolicy = BOTH, label = "Game"),
        activity("creating", "Creating", R.drawable.ic_activity_creating, "Drawing", "Painting", "Crafts", "Pottery", "Sewing", "Design", timePolicy = BOTH, label = "Create"),
        activity("writing", "Writing", R.drawable.ic_activity_writing, "Story", "Poetry", "Blog", "Personal writing", timePolicy = BOTH, label = "Write"),
        activity("photography", "Photography", R.drawable.ic_activity_photography, "Taking photos", "Filming", "Editing photos", "Editing video", timePolicy = BOTH, label = "Photos"),
        activity("gardening", "Gardening", R.drawable.ic_activity_gardening, "Watering", "Planting", "Pruning", "Plant care", timePolicy = BOTH, label = "Garden"),
        activity("exploring", "Exploring", R.drawable.ic_activity_exploring, "City", "Nature", "Museum", "Sightseeing", timePolicy = BOTH, label = "Explore"),
        activity("event", "Event", R.drawable.ic_activity_event, "Cinema", "Theatre", "Festival", "Exhibition", "Live show", timePolicy = BOTH),
        activity("relaxing", "Relaxing", R.drawable.ic_activity_relaxing, "At home", "Outside", "Quiet time", "Screen time", timePolicy = BOTH, label = "Relax")
    )),
    ActivityGroup("social", "Social", R.drawable.ic_activity_meeting_people, listOf(
        activity("meeting-people", "Meeting people", R.drawable.ic_activity_meeting_people, "Catch-up", "New people", "Group gathering", timePolicy = BOTH, label = "Meet"),
        activity("family", "Family time", R.drawable.ic_activity_family, "At home", "Outing", "Meal together", timePolicy = BOTH, label = "Family"),
        activity("friends", "With friends", R.drawable.ic_activity_friends, "Hanging out", "Meal together", "Outing", timePolicy = BOTH, label = "Friends"),
        activity("date", "Date", R.drawable.ic_activity_date, "Meal", "Coffee", "Walk", "Outing", timePolicy = BOTH),
        activity("visiting", "Visiting", R.drawable.ic_activity_visiting, "Family", "Friends", "Neighbours", timePolicy = BOTH, label = "Visit"),
        activity("chatting", "Chatting", R.drawable.ic_activity_chatting, "In person", "Online", "Group chat", timePolicy = BOTH, label = "Chat"),
        activity("celebrating", "Celebrating", R.drawable.ic_activity_celebrating, "Birthday", "Wedding", "Anniversary", "Festival", "Achievement", timePolicy = BOTH, label = "Celebrate"),
        activity("volunteering", "Volunteering", R.drawable.ic_activity_volunteering, "Community", "Environment", "Food", "Animals", "Education", timePolicy = BOTH, label = "Volunteer"),
        activity("pet-time", "Pet time", R.drawable.ic_activity_pet_time, "Playing", "Cuddling", "Training", "Outside", timePolicy = BOTH),
        activity("community", "Community", R.drawable.ic_activity_community, "Club", "Meetup", "Group activity", "Local gathering", timePolicy = BOTH)
    )),
    ActivityGroup("home", "Home", R.drawable.ic_activity_tidying, listOf(
        activity("cleaning", "Cleaning", R.drawable.ic_activity_cleaning, "Home", "Kitchen", "Bathroom", "Bedroom", "Car", timePolicy = BOTH, label = "Clean"),
        activity("laundry", "Laundry", R.drawable.ic_activity_laundry, "Washing", "Drying", "Folding", "Ironing", timePolicy = BOTH),
        activity("dishes", "Dishes", R.drawable.ic_activity_dishes, "Washing up", "Loading dishwasher", "Putting away", timePolicy = BOTH),
        activity("tidying", "Tidying", R.drawable.ic_activity_tidying, "Room", "Desk", "Wardrobe", "Decluttering", timePolicy = BOTH, label = "Tidy"),
        activity("maintenance", "Maintenance", R.drawable.ic_activity_maintenance, "Home repair", "DIY", "Vehicle", "Electronics", timePolicy = BOTH, label = "Repair"),
        activity("moving-home", "Moving home", R.drawable.ic_activity_moving_home, "Packing", "Unpacking", "Moving furniture", timePolicy = BOTH, label = "Move home"),
        activity("childcare", "Childcare", R.drawable.ic_activity_childcare, "Playing", "Feeding", "School run", "Bedtime", "Caring", timePolicy = BOTH),
        activity("pet-care", "Pet care", R.drawable.ic_activity_pet_care, "Feeding", "Grooming", "Cleaning", "Vet visit", timePolicy = BOTH),
        activity("meal-prep", "Meal preparation", R.drawable.ic_activity_meal_prep, "Batch cooking", "Packing lunch", "Chopping", "Meal planning", timePolicy = BOTH, label = "Meal prep"),
        activity("home-admin", "Home admin", R.drawable.ic_activity_home_admin, "Bills", "Budget", "Paperwork", "Scheduling", "Organising files", timePolicy = BOTH)
    )),
    ActivityGroup("errands", "Errands", R.drawable.ic_activity_shopping, listOf(
        activity("shopping", "Shopping", R.drawable.ic_activity_shopping, "Clothes", "Household", "Electronics", "Gifts", "Online", timePolicy = BOTH, label = "Shop"),
        activity("groceries", "Groceries", R.drawable.ic_activity_groceries, "Supermarket", "Market", "Local shop", "Online order", timePolicy = BOTH),
        activity("collecting", "Collecting", R.drawable.ic_activity_collecting, "Parcel", "Order", "Food", "Documents", timePolicy = AT_ONLY, label = "Collect"),
        activity("delivery", "Delivery", R.drawable.ic_activity_delivery, "Parcel", "Food", "Documents", "Drop-off", timePolicy = AT_ONLY),
        activity("appointment", "Appointment", R.drawable.ic_activity_appointment, "Personal", "Professional", "Service", "Consultation", timePolicy = BOTH),
        activity("bank", "Banking", R.drawable.ic_activity_bank, "Bank branch", "ATM", "Online banking", timePolicy = BOTH, label = "Bank"),
        activity("post-office", "Post office", R.drawable.ic_activity_post_office, "Posting", "Collecting", "Courier", timePolicy = BOTH),
        activity("fuel-charge", "Fuel / charge", R.drawable.ic_activity_fuel_charge, "Fuel stop", "EV charging", "Vehicle charging", timePolicy = BOTH),
        activity("queue", "Queuing", R.drawable.ic_activity_queue, "Shop", "Service", "Tickets", "Office", timePolicy = BOTH, label = "Queue"),
        activity("haircut", "Haircut", R.drawable.ic_activity_haircut, "Barber", "Salon", "At home", timePolicy = BOTH)
    )),
    ActivityGroup("wellbeing", "Wellbeing", R.drawable.ic_activity_meditating, listOf(
        activity("meditating", "Meditating", R.drawable.ic_activity_meditating, "Guided", "Quiet", "Mindfulness", timePolicy = BOTH, label = "Meditate"),
        activity("breathing", "Breathing", R.drawable.ic_activity_breathing, "Breathing exercise", "Quiet pause", timePolicy = BOTH, label = "Breathe"),
        activity("journaling", "Journaling", R.drawable.ic_activity_journaling, "Reflection", "Gratitude", "Daily journal", timePolicy = BOTH, label = "Journal"),
        activity("praying", "Prayer", R.drawable.ic_activity_praying, "At home", "Place of worship", "Quiet reflection", timePolicy = BOTH, label = "Pray"),
        activity("health-visit", "Health visit", R.drawable.ic_activity_health_visit, "Doctor", "Dentist", "Check-up", "Test", "Pharmacy", timePolicy = BOTH),
        activity("therapy", "Therapy", R.drawable.ic_activity_therapy, "Session", "Physiotherapy", "Counselling", timePolicy = BOTH),
        activity("self-care", "Self-care", R.drawable.ic_activity_self_care, "Quiet time", "Personal care", "Digital break", timePolicy = BOTH),
        activity("recovering", "Recovering", R.drawable.ic_activity_recovering, "Rest day", "At home", "After exercise", timePolicy = BOTH, label = "Recover"),
        activity("medicine", "Taking medicine", R.drawable.ic_activity_medicine, "Scheduled dose", "Vitamins", "Supplements", timePolicy = AT_ONLY, label = "Medicine"),
        activity("check-in", "Check-in", R.drawable.ic_activity_check_in, "Feeling good", "Feeling okay", "Taking it easy", "Need a break", timePolicy = AT_ONLY)
    ))
)

internal val ACTIVITY_OPTIONS = ACTIVITY_GROUPS.flatMap { it.activities }
