import type { GeneratedRoleplay, GeneratedTopic } from "./tutor.js";

export type SeedTopicItem = {
  id: string;
  language: string;
  category: "travel" | "food" | "work" | "daily_life" | "grammar" | "culture";
  sourceKind: "system" | "community";
  authorHandle: string | null;
  level: string;
  learnersCount: number;
  prompt: string;
  generated: GeneratedTopic;
};

export type SeedRoleplayItem = {
  id: string;
  language: string;
  category: "travel" | "food" | "work" | "housing" | "health" | "social";
  sourceKind: "system" | "community";
  authorHandle: string | null;
  level: string;
  practicedCount: number;
  generated: GeneratedRoleplay;
};

const SEED_TOPICS: SeedTopicItem[] = [
  // Spanish — Zuno Official & Community
  {
    id: "seed-topic-es-cafe",
    language: "es",
    category: "food",
    sourceKind: "system",
    authorHandle: null,
    level: "B1",
    learnersCount: 312,
    prompt: "Ordering specialty coffee, pastries, and the bill naturally in Spain",
    generated: {
      title: "Pedir café de especialidad",
      translation: "Ordering specialty coffee",
      category: "food",
      lessons: [
        { title: "Saludar y pedir mesa", translation: "Greeting & getting a table", objective: "Greet the barista naturally and ask for a table or takeaway." },
        { title: "Elegir café y tipo de leche", translation: "Choosing coffee & milk", objective: "Order cortado, café con leche, or oat milk with confidence." },
        { title: "Acompañar con algo dulce", translation: "Adding pastries or toast", objective: "Ask what is freshly baked and order breakfast items." },
        { title: "Preguntar por ingredientes", translation: "Asking about ingredients", objective: "Clarify allergens, sugar, or decaf options politely." },
        { title: "Pedir la cuenta y pagar", translation: "Asking for the bill & paying", objective: "Ask for the bill and pay by card or cash like a local." },
      ],
    },
  },
  {
    id: "seed-topic-es-tren",
    language: "es",
    category: "travel",
    sourceKind: "community",
    authorHandle: "@carlos.v",
    level: "B1",
    learnersCount: 142,
    prompt: "Taking high-speed trains across Spain: tickets, platforms, and seat changes",
    generated: {
      title: "Viajar en tren por España",
      translation: "Train travel across Spain",
      category: "travel",
      lessons: [
        { title: "Comprar billete de ida y vuelta", translation: "Buying a round-trip ticket", objective: "Buy a train ticket and choose departure times." },
        { title: "Encontrar la vía y el vagón", translation: "Finding platform & coach", objective: "Ask station staff for the right platform and car number." },
        { title: "Equipaje y control de acceso", translation: "Luggage & boarding check", objective: "Navigate security and luggage storage smoothly." },
        { title: "Cambiar de asiento a bordo", translation: "Swapping seats onboard", objective: "Politely ask another passenger to switch or check seats." },
        { title: "Conexiones y retrasos", translation: "Connections & delays", objective: "Ask about delays and connecting metro or bus lines." },
      ],
    },
  },
  {
    id: "seed-topic-es-tapas",
    language: "es",
    category: "food",
    sourceKind: "community",
    authorHandle: "@carlos.v",
    level: "B1",
    learnersCount: 186,
    prompt: "Ordering tapas and sharing plates with friends in Madrid",
    generated: {
      title: "Café y tapas en Madrid",
      translation: "Ordering coffee & tapas naturally",
      category: "food",
      lessons: [
        { title: "Pedir un café con leche", translation: "Ordering coffee with milk", objective: "Order morning coffee naturally using ¿Me pones…?" },
        { title: "Elegir leche y tamaño", translation: "Milk options & sizes", objective: "Specify plant milk, temperature, or takeaway." },
        { title: "Pedir tapas para compartir", translation: "Ordering tapas to share", objective: "Order raciones and tapas for the table." },
        { title: "Preguntar por recomendaciones", translation: "Asking for house specials", objective: "Ask the waiter what they recommend today." },
        { title: "Pedir la cuenta al camarero", translation: "Settling the bill", objective: "Get the waiter's attention and split or pay the bill." },
      ],
    },
  },
  {
    id: "seed-topic-es-startup",
    language: "es",
    category: "work",
    sourceKind: "system",
    authorHandle: null,
    level: "B2",
    learnersCount: 204,
    prompt: "Daily standups, Slack follow-ups, and product meetings in Spanish",
    generated: {
      title: "Reuniones de equipo en startup",
      translation: "Startup team meetings",
      category: "work",
      lessons: [
        { title: "Resumen en el daily standup", translation: "Sharing your daily update", objective: "Explain what you shipped yesterday and today's focus." },
        { title: "Explicar un bloqueo técnico", translation: "Explaining a blocker", objective: "Describe a blocker clearly and ask a teammate for help." },
        { title: "Proponer una mejora", translation: "Proposing an idea", objective: "Pitch a product or workflow improvement persuasively." },
        { title: "Dar feedback constructivo", translation: "Giving constructive feedback", objective: "Share polite, actionable feedback in a review." },
        { title: "Acordar próximos pasos", translation: "Aligning on next steps", objective: "Wrap up a call with clear owners and deadlines." },
      ],
    },
  },
  {
    id: "seed-topic-es-vecinos",
    language: "es",
    category: "daily_life",
    sourceKind: "community",
    authorHandle: "@priya.s",
    level: "A2",
    learnersCount: 94,
    prompt: "Everyday small talk with neighbors, shopkeepers, and new friends",
    generated: {
      title: "Charlas cotidianas en el barrio",
      translation: "Everyday neighborhood small talk",
      category: "daily_life",
      lessons: [
        { title: "Presentarte a un vecino", translation: "Introducing yourself", objective: "Introduce yourself warmly in the elevator or hallway." },
        { title: "Hablar del tiempo y el finde", translation: "Weather & weekend plans", objective: "Keep light small talk going about the weekend." },
        { title: "Pedir un pequeño favor", translation: "Asking a small favor", objective: "Ask a neighbor to hold a package politely." },
        { title: "En la panadería del barrio", translation: "At the local bakery", objective: "Chat briefly with local shopkeepers while buying bread." },
        { title: "Quedar para tomar algo", translation: "Inviting someone for a drink", objective: "Suggest grabbing a coffee or caña sometime." },
      ],
    },
  },
  // English fallback seeds
  {
    id: "seed-topic-en-standup",
    language: "en",
    category: "work",
    sourceKind: "system",
    authorHandle: null,
    level: "B1",
    learnersCount: 275,
    prompt: "Leading product standups and cross-functional check-ins in English",
    generated: {
      title: "Leading product standups",
      translation: "Leading product standups",
      category: "work",
      lessons: [
        { title: "Opening the standup", translation: "Opening the standup", objective: "Kick off a team sync concisely and warmly." },
        { title: "Sharing progress clearly", translation: "Sharing progress clearly", objective: "Summarize milestones without rambling." },
        { title: "Flagging blockers early", translation: "Flagging blockers early", objective: "Raise risks and ask for support constructively." },
        { title: "Handling scope questions", translation: "Handling scope questions", objective: "Clarify trade-offs and timelines on the spot." },
        { title: "Wrapping up with action items", translation: "Wrapping up with action items", objective: "Confirm owners and next steps before ending." },
      ],
    },
  },
  {
    id: "seed-topic-en-travel",
    language: "en",
    category: "travel",
    sourceKind: "community",
    authorHandle: "@mateo.r",
    level: "B1",
    learnersCount: 163,
    prompt: "Navigating airports, hotels, and local transit smoothly in English",
    generated: {
      title: "City break travel essentials",
      translation: "City break travel essentials",
      category: "travel",
      lessons: [
        { title: "Airport check-in & bags", translation: "Airport check-in & bags", objective: "Check luggage and ask for a window or aisle seat." },
        { title: "Immigration questions", translation: "Immigration questions", objective: "Answer purpose-of-stay questions calmly." },
        { title: "Hotel early check-in", translation: "Hotel early check-in", objective: "Request early check-in or luggage storage." },
        { title: "Asking locals for transit tips", translation: "Asking locals for transit tips", objective: "Ask which subway line goes downtown." },
        { title: "Sorting out a booking issue", translation: "Sorting out a booking issue", objective: "Resolve a room or reservation mix-up politely." },
      ],
    },
  },
];

const SEED_ROLEPLAYS: SeedRoleplayItem[] = [
  {
    id: "seed-rp-es-cafe",
    language: "es",
    category: "food",
    sourceKind: "system",
    authorHandle: null,
    level: "B1",
    practicedCount: 418,
    generated: {
      title: "Ordering at a café",
      scenario: "Order coffee and breakfast at a busy café in Madrid",
      userRole: "Customer",
      aiRole: "Barista",
      setting: "You step up to the counter at a busy specialty café in Malasaña. Order a drink and something to eat, handle one sold-out item, and pay.",
      category: "food",
    },
  },
  {
    id: "seed-rp-es-airport",
    language: "es",
    category: "travel",
    sourceKind: "system",
    authorHandle: null,
    level: "B1",
    practicedCount: 305,
    generated: {
      title: "Airport check-in",
      scenario: "Check in for a flight and resolve a carry-on weight issue",
      userRole: "Traveler",
      aiRole: "Agent",
      setting: "You are checking in at Madrid-Barajas Airport. Your carry-on is slightly overweight and your gate just changed — sort out your boarding pass and seat.",
      category: "travel",
    },
  },
  {
    id: "seed-rp-es-luggage",
    language: "es",
    category: "travel",
    sourceKind: "community",
    authorHandle: "@mateo.r",
    level: "B1",
    practicedCount: 214,
    generated: {
      title: "Lost luggage at Barajas",
      scenario: "Report a delayed suitcase and arrange hotel delivery",
      userRole: "Traveler",
      aiRole: "Airline Agent",
      setting: "Your suitcase didn't arrive on your flight to Madrid. Describe your bag, file a claim, and arrange delivery to your hotel.",
      category: "travel",
    },
  },
  {
    id: "seed-rp-es-interview",
    language: "es",
    category: "work",
    sourceKind: "community",
    authorHandle: "@lucia.m",
    level: "B2",
    practicedCount: 189,
    generated: {
      title: "Startup product interview",
      scenario: "Interview for a product role at a Madrid tech startup",
      userRole: "Candidate",
      aiRole: "Hiring Lead",
      setting: "You are on a friendly first-round call with the Hiring Lead at a Madrid startup. Share a recent project, handle a follow-up question on teamwork, and ask about the team.",
      category: "work",
    },
  },
  {
    id: "seed-rp-es-flat",
    language: "es",
    category: "housing",
    sourceKind: "community",
    authorHandle: "@carlos.v",
    level: "B1",
    practicedCount: 154,
    generated: {
      title: "Renting an apartment",
      scenario: "View a sunny apartment in Chamberí and ask about utilities",
      userRole: "Tenant",
      aiRole: "Landlord",
      setting: "You are touring a one-bedroom flat in Madrid. Ask what utilities are included, check the deposit terms, and ask about move-in dates.",
      category: "housing",
    },
  },
  {
    id: "seed-rp-en-interview",
    language: "en",
    category: "work",
    sourceKind: "system",
    authorHandle: null,
    level: "B2",
    practicedCount: 340,
    generated: {
      title: "Job interview",
      scenario: "First-round interview with a tech recruiter",
      userRole: "Candidate",
      aiRole: "Recruiter",
      setting: "You are speaking with a recruiter about a new role. Walk through your background, explain why you are interested, and ask a thoughtful question about the team.",
      category: "work",
    },
  },
  {
    id: "seed-rp-en-hotel",
    language: "en",
    category: "travel",
    sourceKind: "community",
    authorHandle: "@priya.s",
    level: "B1",
    practicedCount: 178,
    generated: {
      title: "Checking into a hotel",
      scenario: "Check in early and request a quiet room",
      userRole: "Guest",
      aiRole: "Receptionist",
      setting: "You arrive at your hotel two hours before check-in after an overnight flight. Ask if a quiet room is ready or arrange luggage storage.",
      category: "travel",
    },
  },
];

export function seedTopicsForLanguage(language: string): SeedTopicItem[] {
  const exact = SEED_TOPICS.filter((item) => item.language === language);
  return exact.length > 0 ? exact : SEED_TOPICS.filter((item) => item.language === "es");
}

export function findSeedTopic(id: string): SeedTopicItem | undefined {
  return SEED_TOPICS.find((item) => item.id === id);
}

export function seedRoleplaysForLanguage(language: string): SeedRoleplayItem[] {
  const exact = SEED_ROLEPLAYS.filter((item) => item.language === language);
  return exact.length > 0 ? exact : SEED_ROLEPLAYS.filter((item) => item.language === "es");
}

export function findSeedRoleplay(id: string): SeedRoleplayItem | undefined {
  return SEED_ROLEPLAYS.find((item) => item.id === id);
}
