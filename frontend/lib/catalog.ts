export type Property = {
  id: string;
  name: string;
  destination: string;
  type: "resort" | "business";
  description: string;
  amenities: string[];
  fromRate: number;
  rooms: { id: string; name: string; description: string; rate: number }[];
};

export const properties: Property[] = [
  {
    id: "azure-grove-miami",
    name: "Azure Grove Miami",
    destination: "Miami",
    type: "resort",
    description:
      "A calm waterfront base for bright days, shared meals, and unhurried evenings.",
    amenities: ["Pool", "Kids club", "Beach access", "Breakfast"],
    fromRate: 289,
    rooms: [
      {
        id: "garden-suite",
        name: "Garden suite",
        description: "A spacious suite with a private terrace.",
        rate: 289,
      },
      {
        id: "family-loft",
        name: "Family loft",
        description: "Two sleeping zones and room to gather.",
        rate: 389,
      },
    ],
  },
  {
    id: "harbor-lantern-miami",
    name: "Harbor Lantern Miami",
    destination: "Miami",
    type: "resort",
    description:
      "A light-filled retreat near the water with generous rooms and a playful pool deck.",
    amenities: ["Pool", "Waterfront", "Bikes", "Breakfast"],
    fromRate: 319,
    rooms: [
      {
        id: "sunset-room",
        name: "Sunset room",
        description: "A warm, flexible room with bay views.",
        rate: 319,
      },
    ],
  },
  {
    id: "civic-house-austin",
    name: "Civic House Austin",
    destination: "Austin",
    type: "business",
    description:
      "Quiet workspaces, thoughtful service, and a central address for focused stays.",
    amenities: ["Workspace", "Meeting rooms", "Gym", "Late checkout"],
    fromRate: 219,
    rooms: [
      {
        id: "studio-king",
        name: "Studio king",
        description: "A comfortable room with an ergonomic workspace.",
        rate: 219,
      },
    ],
  },
  {
    id: "juniper-square-portland",
    name: "Juniper Square Portland",
    destination: "Portland",
    type: "business",
    description:
      "A neighborhood stay with a slower rhythm and room to settle in.",
    amenities: ["Workspace", "Coffee bar", "Bikes", "Garden"],
    fromRate: 199,
    rooms: [
      {
        id: "courtyard-room",
        name: "Courtyard room",
        description: "A quiet room facing the planted courtyard.",
        rate: 199,
      },
    ],
  },
];
