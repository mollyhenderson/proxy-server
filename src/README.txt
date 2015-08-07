Molly Henderson
meh111
EECS 325 Project 1

This program creates a basic proxy server. The default port number is 5023.
Run the program by calling java proxyd [-port <portnum>] from the command line.
I tested the proxy using Chrome and Firefox, with the sites case.edu, whitehouse.gov, 
cim.edu, and cnn.com.

I also implemented DNS caching; cached values are deleted after 30 seconds.