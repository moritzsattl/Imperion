# ImperionV2

I am pleased to introduce the improved version of the game agent. I was not entirely satisfied with the previous program, which led me to apply the knowledge and experience gained from the course to develop a fundamentally new version of the agent.

A key focus of the new program is to ensure that the agent’s actions are understandable and transparent. I spent countless hours analyzing the implementation of the Monte Carlo Tree Search (MCTS) algorithm to identify potential errors. Through this in-depth review, I discovered that even small changes in the weighting of individual heuristics had a noticeable effect on the agent’s behavior (e.g., aggressive versus defensive, unit selection). This exploration of the agent's decision-making process was particularly rewarding.

To further enhance the transparency of the agent’s actions, I opted to use only a few significant heuristics rather than many opaque ones. This decision has made the agent's behavior more predictable and aligned with strategic goals.

The most important improvement, however, was reducing the search space by limiting the agent's action options per game state. I defined several macro-actions such as EXPAND, EXPLORE, and CONQUER, which group multiple atomic actions into a single command. The agent can then select one of these macro-actions, akin to a general giving strategic orders in a war.

This new approach allows the agent to make decisions more efficiently and strategically, improving both performance and clarity.
 
# Imperion

This version of the game agent was built as a course project at the University of Technology in Vienna. The development and its challenges were summarized in the document below.

[SGP___Final_Hand_in.pdf](https://github.com/user-attachments/files/17227309/SGP___Final_Hand_in.pdf)

Interal Note regarding the Commit History: All commits are duplicated, since I had to sign them. There would have probably been a better way to accomplish it, but it's not the end of the world.
