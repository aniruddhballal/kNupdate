package com.knupdate.app

internal data class ActivityOption(
    val id: String,
    val title: String,
    val label: String,
    val icon: Int,
    val bodies: List<String>,
    val isRange: Boolean
)

internal data class ActivityGroup(val id: String, val label: String, val icon: Int, val activities: List<ActivityOption>)

private fun activity(id: String, title: String, icon: Int, vararg details: String,
    single: Boolean = false, label: String = title) = ActivityOption(id, title, label, icon, details.toList(), !single)

// A fixed, standalone catalog. No user-created activities or remote catalog dependency.
internal val ACTIVITY_GROUPS = listOf(
    ActivityGroup("everyday", "Everyday", R.drawable.ic_activity_location, listOf(
        activity("location", "Current location", R.drawable.ic_activity_location, "Home", "Work", "School", "University", "Gym", "Outside", "Away", single = true, label = "Location"),
        activity("waking", "Waking up", R.drawable.ic_activity_sun, "Home", "Away", "After a nap", single = true, label = "Wake up"),
        activity("sleeping", "Sleeping", R.drawable.ic_activity_sleep, "Night's sleep", "Nap", "Home", "Away", label = "Sleep"),
        activity("resting", "Resting", R.drawable.ic_activity_rest, "At home", "Short break", "Lying down", label = "Rest"),
        activity("eating", "Eating", R.drawable.ic_activity_eat, "Breakfast", "Lunch", "Dinner", "Snack", "Brunch", label = "Eat"),
        activity("drinking", "Drinking", R.drawable.ic_activity_drink, "Water", "Coffee", "Tea", "Juice", "Smoothie", "Milk", single = true, label = "Drink"),
        activity("cooking", "Cooking", R.drawable.ic_activity_cook, "Breakfast", "Lunch", "Dinner", "Baking", "Snack", label = "Cook"),
        activity("showering", "Bathing", R.drawable.ic_activity_water, "Shower", "Bath", label = "Bathe"),
        activity("grooming", "Grooming", R.drawable.ic_activity_care, "Brushing teeth", "Skincare", "Shaving", "Hair care", label = "Groom"),
        activity("getting-ready", "Getting ready", R.drawable.ic_activity_care, "For work", "For school", "To go out", "For bed", label = "Get ready")
    )),
    ActivityGroup("work", "Work", R.drawable.ic_activity_work, listOf(
        activity("working", "Working", R.drawable.ic_activity_work, "Office", "Home", "Coworking space", "On site", label = "Work"),
        activity("meeting", "Meeting", R.drawable.ic_activity_people, "Team meeting", "One-to-one", "Client meeting", "Workshop"),
        activity("video-call", "Video call", R.drawable.ic_activity_video, "Work", "Family", "Friends", "Appointment"),
        activity("phone-call", "Phone call", R.drawable.ic_activity_phone, "Work", "Family", "Friends", "Appointment"),
        activity("email", "Email", R.drawable.ic_activity_mail, "Reading", "Writing", "Clearing inbox"),
        activity("planning", "Planning", R.drawable.ic_activity_list, "Day", "Week", "Project", "Schedule", label = "Plan"),
        activity("focus", "Focused work", R.drawable.ic_activity_focus, "Writing", "Coding", "Design", "Analysis", "Problem solving", label = "Focus"),
        activity("presenting", "Presenting", R.drawable.ic_activity_video, "Presentation", "Demo", "Talk", "Teaching", label = "Present"),
        activity("interview", "Interview", R.drawable.ic_activity_people, "Job interview", "Interviewing", "Practice interview"),
        activity("work-break", "Work break", R.drawable.ic_activity_drink, "Coffee break", "Lunch break", "Screen break", "Walk break", label = "Break")
    )),
    ActivityGroup("learning", "Learning", R.drawable.ic_activity_study, listOf(
        activity("studying", "Studying", R.drawable.ic_activity_study, "Home", "Library", "School", "University", "Study group", label = "Study"),
        activity("reading", "Reading", R.drawable.ic_activity_study, "Book", "Article", "News", "Comics", "Audiobook", label = "Read"),
        activity("class", "Class", R.drawable.ic_activity_study, "In person", "Online", "Lab", "Tutorial"),
        activity("lecture", "Lecture", R.drawable.ic_activity_video, "In person", "Online", "Recorded"),
        activity("homework", "Homework", R.drawable.ic_activity_write, "Assignment", "Project", "Problem set", "Lab report"),
        activity("revision", "Revision", R.drawable.ic_activity_list, "Notes", "Flashcards", "Practice questions", "Past papers"),
        activity("exam", "Exam", R.drawable.ic_activity_write, "Written exam", "Online exam", "Practical", "Quiz"),
        activity("research", "Research", R.drawable.ic_activity_focus, "Reading papers", "Experiments", "Data analysis", "Fieldwork"),
        activity("practice", "Practice", R.drawable.ic_activity_create, "Language", "Instrument", "Skill", "Driving", "Public speaking"),
        activity("course", "Course", R.drawable.ic_activity_video, "Online lesson", "Workshop", "Training", "Certification")
    )),
    ActivityGroup("movement", "Movement", R.drawable.ic_activity_run, listOf(
        activity("exercising", "Exercising", R.drawable.ic_activity_exercise, "Gym", "Outside", "Home", "Workout class", label = "Exercise"),
        activity("run", "Run", R.drawable.ic_activity_run, "5k", "10k", "Short run", "Long run", "Treadmill", "Trail run"),
        activity("walk", "Walk", R.drawable.ic_activity_walk, "Outside", "Park", "With a pet", "With company", "Treadmill"),
        activity("cycle", "Cycle", R.drawable.ic_activity_cycle, "Road", "Trail", "Indoor", "Commute"),
        activity("gym", "Gym", R.drawable.ic_activity_exercise, "Strength", "Cardio", "Full body", "Upper body", "Lower body"),
        activity("yoga", "Yoga", R.drawable.ic_activity_wellbeing, "Home", "Class", "Outside"),
        activity("stretch", "Stretching", R.drawable.ic_activity_walk, "Warm-up", "Cool-down", "Mobility", "Desk break", label = "Stretch"),
        activity("swim", "Swim", R.drawable.ic_activity_water, "Pool", "Open water", "Lesson"),
        activity("hike", "Hike", R.drawable.ic_activity_mountain, "Trail", "Hill", "Mountain", "Nature walk"),
        activity("sport", "Sport", R.drawable.ic_activity_ball, "Football", "Cricket", "Basketball", "Badminton", "Tennis", "Table tennis", "Volleyball", "Dance", "Martial arts", "Skating")
    )),
    ActivityGroup("travel", "Travel", R.drawable.ic_activity_travel, listOf(
        activity("travelling", "Travelling", R.drawable.ic_activity_travel, "Walking", "Driving", "Public transport", "Taxi", "Passenger", label = "Travel"),
        activity("commute", "Commute", R.drawable.ic_activity_travel, "To work", "From work", "To school", "From school", "To university", "From university"),
        activity("drive", "Drive", R.drawable.ic_activity_travel, "Car", "Motorcycle", "Scooter", "Road trip"),
        activity("ride", "Ride", R.drawable.ic_activity_travel, "Taxi", "Rideshare", "Auto-rickshaw", "Passenger"),
        activity("bus", "Bus", R.drawable.ic_activity_bus, "Local bus", "Intercity bus", "Shuttle"),
        activity("train", "Train", R.drawable.ic_activity_train, "Metro", "Local train", "Intercity train", "Tram"),
        activity("flight", "Flight", R.drawable.ic_sent, "Domestic", "International", "Connecting flight"),
        activity("arrived", "Arrived", R.drawable.ic_activity_location, "Home", "Work", "School", "University", "Gym", "Station", "Airport", "Destination", single = true),
        activity("departed", "Departed", R.drawable.ic_activity_location, "Home", "Work", "School", "University", "Gym", "Station", "Airport", "Destination", single = true),
        activity("waiting", "Waiting", R.drawable.ic_activity_clock, "For transport", "For someone", "At a station", "At an airport", label = "Wait")
    )),
    ActivityGroup("leisure", "Leisure", R.drawable.ic_activity_music, listOf(
        activity("music", "Music", R.drawable.ic_activity_music, "Listening", "Playing", "Singing", "Concert"),
        activity("watching", "Watching", R.drawable.ic_activity_video, "Movie", "Series", "Video", "Documentary", "Live sport", label = "Watch"),
        activity("gaming", "Gaming", R.drawable.ic_activity_game, "Console", "PC", "Mobile", "Board game", "Card game", label = "Game"),
        activity("creating", "Creating", R.drawable.ic_activity_create, "Drawing", "Painting", "Crafts", "Pottery", "Sewing", "Design", label = "Create"),
        activity("writing", "Writing", R.drawable.ic_activity_write, "Story", "Poetry", "Blog", "Personal writing", label = "Write"),
        activity("photography", "Photography", R.drawable.ic_activity_camera, "Taking photos", "Filming", "Editing photos", "Editing video", label = "Photos"),
        activity("gardening", "Gardening", R.drawable.ic_activity_leaf, "Watering", "Planting", "Pruning", "Plant care", label = "Garden"),
        activity("exploring", "Exploring", R.drawable.ic_activity_mountain, "City", "Nature", "Museum", "Sightseeing", label = "Explore"),
        activity("event", "Event", R.drawable.ic_activity_ticket, "Cinema", "Theatre", "Festival", "Exhibition", "Live show"),
        activity("relaxing", "Relaxing", R.drawable.ic_activity_rest, "At home", "Outside", "Quiet time", "Screen time", label = "Relax")
    )),
    ActivityGroup("social", "Social", R.drawable.ic_activity_people, listOf(
        activity("meeting-people", "Meeting people", R.drawable.ic_activity_people, "Catch-up", "New people", "Group gathering", label = "Meet"),
        activity("family", "Family time", R.drawable.ic_activity_people, "At home", "Outing", "Meal together", label = "Family"),
        activity("friends", "With friends", R.drawable.ic_activity_people, "Hanging out", "Meal together", "Outing", label = "Friends"),
        activity("date", "Date", R.drawable.ic_activity_heart, "Meal", "Coffee", "Walk", "Outing"),
        activity("visiting", "Visiting", R.drawable.ic_activity_home, "Family", "Friends", "Neighbours", label = "Visit"),
        activity("chatting", "Chatting", R.drawable.ic_activity_chat, "In person", "Online", "Group chat", label = "Chat"),
        activity("celebrating", "Celebrating", R.drawable.ic_activity_gift, "Birthday", "Wedding", "Anniversary", "Festival", "Achievement", label = "Celebrate"),
        activity("volunteering", "Volunteering", R.drawable.ic_activity_heart, "Community", "Environment", "Food", "Animals", "Education", label = "Volunteer"),
        activity("pet-time", "Pet time", R.drawable.ic_activity_pet, "Playing", "Cuddling", "Training", "Outside"),
        activity("community", "Community", R.drawable.ic_activity_people, "Club", "Meetup", "Group activity", "Local gathering")
    )),
    ActivityGroup("home", "Home", R.drawable.ic_activity_home, listOf(
        activity("cleaning", "Cleaning", R.drawable.ic_activity_clean, "Home", "Kitchen", "Bathroom", "Bedroom", "Car", label = "Clean"),
        activity("laundry", "Laundry", R.drawable.ic_activity_laundry, "Washing", "Drying", "Folding", "Ironing"),
        activity("dishes", "Dishes", R.drawable.ic_activity_eat, "Washing up", "Loading dishwasher", "Putting away"),
        activity("tidying", "Tidying", R.drawable.ic_activity_home, "Room", "Desk", "Wardrobe", "Decluttering", label = "Tidy"),
        activity("maintenance", "Maintenance", R.drawable.ic_activity_tools, "Home repair", "DIY", "Vehicle", "Electronics", label = "Repair"),
        activity("moving-home", "Moving home", R.drawable.ic_activity_box, "Packing", "Unpacking", "Moving furniture", label = "Move home"),
        activity("childcare", "Childcare", R.drawable.ic_activity_people, "Playing", "Feeding", "School run", "Bedtime", "Caring"),
        activity("pet-care", "Pet care", R.drawable.ic_activity_pet, "Feeding", "Grooming", "Cleaning", "Vet visit"),
        activity("meal-prep", "Meal preparation", R.drawable.ic_activity_cook, "Batch cooking", "Packing lunch", "Chopping", "Meal planning", label = "Meal prep"),
        activity("home-admin", "Home admin", R.drawable.ic_activity_list, "Bills", "Budget", "Paperwork", "Scheduling", "Organising files")
    )),
    ActivityGroup("errands", "Errands", R.drawable.ic_activity_bag, listOf(
        activity("shopping", "Shopping", R.drawable.ic_activity_bag, "Clothes", "Household", "Electronics", "Gifts", "Online", label = "Shop"),
        activity("groceries", "Groceries", R.drawable.ic_activity_bag, "Supermarket", "Market", "Local shop", "Online order"),
        activity("collecting", "Collecting", R.drawable.ic_activity_box, "Parcel", "Order", "Food", "Documents", single = true, label = "Collect"),
        activity("delivery", "Delivery", R.drawable.ic_activity_box, "Parcel", "Food", "Documents", "Drop-off", single = true),
        activity("appointment", "Appointment", R.drawable.ic_activity_clock, "Personal", "Professional", "Service", "Consultation"),
        activity("bank", "Banking", R.drawable.ic_activity_bank, "Bank branch", "ATM", "Online banking", label = "Bank"),
        activity("post-office", "Post office", R.drawable.ic_activity_mail, "Posting", "Collecting", "Courier"),
        activity("fuel-charge", "Fuel / charge", R.drawable.ic_activity_charge, "Fuel stop", "EV charging", "Vehicle charging"),
        activity("queue", "Queuing", R.drawable.ic_activity_people, "Shop", "Service", "Tickets", "Office", label = "Queue"),
        activity("haircut", "Haircut", R.drawable.ic_activity_care, "Barber", "Salon", "At home")
    )),
    ActivityGroup("wellbeing", "Wellbeing", R.drawable.ic_activity_wellbeing, listOf(
        activity("meditating", "Meditating", R.drawable.ic_activity_wellbeing, "Guided", "Quiet", "Mindfulness", label = "Meditate"),
        activity("breathing", "Breathing", R.drawable.ic_activity_leaf, "Breathing exercise", "Quiet pause", label = "Breathe"),
        activity("journaling", "Journaling", R.drawable.ic_activity_write, "Reflection", "Gratitude", "Daily journal", label = "Journal"),
        activity("praying", "Prayer", R.drawable.ic_activity_wellbeing, "At home", "Place of worship", "Quiet reflection", label = "Pray"),
        activity("health-visit", "Health visit", R.drawable.ic_activity_health, "Doctor", "Dentist", "Check-up", "Test", "Pharmacy"),
        activity("therapy", "Therapy", R.drawable.ic_activity_heart, "Session", "Physiotherapy", "Counselling"),
        activity("self-care", "Self-care", R.drawable.ic_activity_care, "Quiet time", "Personal care", "Digital break"),
        activity("recovering", "Recovering", R.drawable.ic_activity_rest, "Rest day", "At home", "After exercise", label = "Recover"),
        activity("medicine", "Taking medicine", R.drawable.ic_activity_health, "Scheduled dose", "Vitamins", "Supplements", single = true, label = "Medicine"),
        activity("check-in", "Check-in", R.drawable.ic_activity_heart, "Feeling good", "Feeling okay", "Taking it easy", "Need a break", single = true)
    ))
)

internal val ACTIVITY_OPTIONS = ACTIVITY_GROUPS.flatMap { it.activities }
