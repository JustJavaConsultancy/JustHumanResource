# Recruitment Testing Guide

A simple step by step guide for testing the full recruitment journey in JustHumanResource, from requesting a new staff member to onboarding the successful candidate.

## Before You Start

Prepare these test users and test records before starting.

| What You Need | Why You Need It |
| --- | --- |
| HR user | To open Recruitment and manage job openings, applications, interviews, offers, and onboarding. |
| Employee user | To raise a Staff Requisition if the company uses employee self service for requests. |
| Approver or Manager user | To approve the Staff Requisition and review job publication when required. |
| Finance Officer user | To approve an employment offer if the offer approval is assigned to Finance. |
| Test candidate information | Use a fake name, email address, phone number, and address. Do not use a real person without permission. |
| Existing Department, Job Grade, Job Step, and Pay Group | These are needed when creating the staff request, preparing the job, making the offer, and starting onboarding. |

> **Note:** If the required Department, Job Grade, Job Step, or Pay Group does not exist, ask the HR setup user to create them first from the side menu of the HR role.

## What To Record During Testing

- Take a screenshot after each important action.
- Write down the job title used for the test.
- Write down the application reference shown after the candidate applies.
- Write down the candidate name and email address.
- Write down any error message exactly as it appears.
- For every problem, record the user role, page name, button clicked, expected result, and actual result.

## Full Recruitment Flow

Follow these steps in order. Each step depends on the step before it.

## Step 1. Create A Staff Requisition

**Where to go:** Requests, at the side menu of the Employee role or HR role.

1. Sign in as the user who is allowed to request a new staff member.
2. Open Requests from the side menu.
3. Click the button for creating a new request.
4. Select Staff Requisition as the request type.
5. Enter the job title for the new position.
6. Select the Department for the position.
7. Select the Job Grade if the field is shown.
8. Enter the number of people needed.
9. Select the employment type if the field is shown.
10. Enter the target start date.
11. Enter a clear reason for the request.
12. Add any supporting note or attachment if required.
13. Save the request if a Save button is available.
14. Submit the request.

### Expected Result

- The request is saved successfully.
- The request status shows that it is waiting for approval.
- The request appears in the request list for the person who created it.

## Step 2. Approve The Staff Requisition

**Where to go:** Requests, at the side menu of the Approver, Manager, or HR role.

1. Sign out from the request creator account.
2. Sign in as the person expected to approve the request.
3. Open Requests from the side menu.
4. Find the Staff Requisition created in Step 1.
5. Open the request details.
6. Check that the job title, department, number of positions, start date, and reason are correct.
7. Add a short approval comment.
8. Click Approve.
9. If there is another approver, repeat this step with the next approver.

### Expected Result

- The request shows as approved after the final approval.
- A job opening is created for the approved request.
- The approval history shows who approved it and when.

> **Warning:** Also test one rejected staff requisition. The rejected request should not create a job opening.

## Step 3. Confirm The Job Opening Was Created

**Where to go:** Recruitment, at the side menu of the HR role. Then open Job openings.

1. Sign in as HR.
2. Open Recruitment from the side menu.
3. Open Job openings from the Recruitment page.
4. Look for the job title from the approved Staff Requisition.
5. Click Open on the job row.

### Expected Result

- The job opening is visible in the Job openings list.
- The job opening shows the correct job title.
- The number of positions matches the approved Staff Requisition.
- The job opening is not yet published unless it was already approved for publication.

## Step 4. Prepare The Job For Publication

**Where to go:** Job openings, inside Recruitment at the side menu of the HR role. Then open the job.

1. On the job opening page, find the Publication content section.
2. Enter the Job description.
3. Enter the Responsibilities.
4. Enter the Requirements.
5. Enter Preferred qualifications if available.
6. Enter the Location.
7. Enter the Work arrangement, such as onsite, hybrid, or remote.
8. Select the Application open date.
9. Select the Application close date.
10. Click Save and send for review.

### Expected Result

- The publication details are saved.
- The job is sent for review.
- The information entered remains visible when the page is opened again.

## Step 5. Review And Publish The Job

**Where to go:** Job openings, inside Recruitment at the side menu of the HR role or Hiring Manager role. Then open the job.

1. Sign in as the person who should review the job publication.
2. Open Recruitment from the side menu.
3. Open Job openings.
4. Open the job prepared in Step 4.
5. Read the job description, responsibilities, requirements, location, and dates.
6. Click Approve and publish.

### Expected Result

- The job status changes to published.
- The job becomes visible to candidates on the Careers page.

> **Warning:** Also test Return and Cancel. Return should send the job back for correction. Cancel should stop the job from being published.

## Step 6. Check The Job On The Careers Page

**Where to go:** Careers, the public careers page for applicants.

1. Sign out or open a private browsing window.
2. Open the Careers page.
3. Find the published job title.
4. Open the job details.
5. Read the job description, responsibilities, and requirements.

### Expected Result

- The published job appears on the Careers page.
- The job details match what HR entered.
- Jobs that are not published do not appear on the Careers page.

## Step 7. Submit A Candidate Application

**Where to go:** Careers page. Open the published job and use the Apply now form.

1. Open the published job on the Careers page.
2. Find the Apply now form.
3. Enter the candidate first name.
4. Enter the candidate last name.
5. Enter the candidate email address.
6. Enter the candidate phone number.
7. Enter the candidate address if required.
8. Enter LinkedIn or portfolio information if available.
9. Select how the candidate heard about the job if the field is shown.
10. Tick the consent checkbox.
11. Click Submit application.

### Expected Result

- The application is submitted successfully.
- A success page is shown.
- An application reference is shown or available in the system.
- The candidate can later check the application status if a status link is provided.

> **Warning:** Also test submitting without required fields and without ticking consent. The form should show a clear message and should not submit.

## Step 8. Confirm The Application Appears For HR

**Where to go:** Recruitment, at the side menu of the HR role. Then open Applications.

1. Sign in as HR.
2. Open Recruitment from the side menu.
3. Open Applications.
4. Find the candidate application submitted in Step 7.
5. Open the application.

### Expected Result

- The candidate appears in the Applications list.
- The candidate name and email address are correct.
- The role name is correct.
- The application has a status showing it has been submitted or is ready for review.

## Step 9. Review The Candidate Application

**Where to go:** Applications, inside Recruitment at the side menu of the HR role. Then open the candidate application.

1. Read the candidate details.
2. Read the role details.
3. In the action section, enter a short comment.
4. Click Advance to move the candidate forward.

### Expected Result

- The candidate moves to the next recruitment stage.
- The comment is saved in the activity area.
- The application history shows that the candidate moved forward.

> **Warning:** Also test Hold and Reject on a separate candidate. Hold should keep the candidate for later review. Reject should stop the candidate from moving forward.

## Step 10. Schedule An Interview

**Where to go:** Candidate application page, inside Applications under Recruitment.

1. On the candidate application page, find Schedule interview.
2. Enter the interview title.
3. Select the interview start date and time.
4. Select the interview end date and time.
5. Enter the location or meeting link.
6. Enter the coordinator employee number if requested.
7. Enter the panel member employee numbers if requested.
8. Click Schedule.

### Expected Result

- The interview is saved.
- The interview appears in the Interviews section.
- The interview date, time, and status are correct.

> **Warning:** Also test an interview end time that is earlier than the start time. The system should not allow an invalid interview time.

## Step 11. Submit Interview Score

**Where to go:** Interviews section on the candidate application page.

1. Find the interview created in Step 10.
2. Enter the reviewer employee number if requested.
3. Enter the overall score.
4. Select the recommendation, such as Hire, Strong hire, Hold, No hire, or Strong no hire.
5. Enter a short comment.
6. Click Submit.

### Expected Result

- The interview score is saved.
- The score and recommendation are shown under the interview.
- The application can continue to the next stage when the candidate is successful.

## Step 12. Move Candidate To Offer Stage

**Where to go:** Candidate application page, inside Applications under Recruitment.

1. Return to the action section of the candidate application page.
2. Enter a comment saying the candidate passed the interview.
3. Click Advance until the candidate reaches the offer stage.

### Expected Result

- The candidate stage changes to offer stage.
- The Create offer section is available to HR.

## Step 13. Create An Employment Offer

**Where to go:** Create offer section on the candidate application page.

1. Enter the annual gross pay.
2. Confirm the currency.
3. Enter or select the Job Step if the field is shown.
4. Enter or select the Pay Group if the field is shown.
5. Select the proposed start date.
6. Select the offer expiry date.
7. Enter offer terms.
8. Click Create offer.

### Expected Result

- The offer is created and appears in the Offers section.
- The pay, currency, proposed start date, expiry date, and terms are correct.
- The offer is ready for approval if approval is required.

> **Warning:** Also test an offer expiry date that is earlier than the proposed start date. The system should reject dates that do not make sense.

## Step 14. Approve Or Reject The Offer

**Where to go:** Offers section on the candidate application page. Use the HR role or Finance Officer role, depending on who approves offers.

1. Sign in as the offer approver.
2. Open Recruitment from the side menu if the approver uses the Recruitment area.
3. Open Applications.
4. Open the candidate application.
5. Find the offer in the Offers section.
6. Review the pay, dates, job step, pay group, and terms.
7. Enter the approver employee number if requested.
8. Click Approve.

### Expected Result

- The offer status changes to approved.
- The Send button becomes available to HR after approval.

> **Warning:** Also test Reject on a separate offer. A rejected offer should not be sent to the candidate.

## Step 15. Send The Offer To The Candidate

**Where to go:** Offers section on the candidate application page, inside Applications under Recruitment.

1. Sign in as HR.
2. Open the candidate application.
3. Find the approved offer.
4. Click Send.

### Expected Result

- The offer status changes to sent.
- The candidate can see the offer on the application status page if that page is enabled.
- If email sending is enabled, the candidate receives the offer notice.

## Step 16. Candidate Accepts The Offer

**Where to go:** Candidate application status page from the candidate status link.

1. Open the candidate application status page.
2. Find the Offers section.
3. Review the offer details.
4. Click Accept offer.

### Expected Result

- The offer is marked as accepted.
- The application status shows that the offer was accepted.
- The application becomes ready for onboarding.

> **Warning:** Also test Decline offer on a separate candidate. A declined offer should not allow onboarding for that candidate.

## Step 17. Start Candidate Onboarding

**Where to go:** Start onboarding section on the candidate application page, inside Applications under Recruitment.

1. Sign in as HR.
2. Open Recruitment from the side menu.
3. Open Applications.
4. Open the candidate who accepted the offer.
5. Find Start onboarding.
6. Select or enter the Department.
7. Select or enter the Manager if needed.
8. Select or enter the Job Step.
9. Select or enter the Pay Group.
10. Enter employee group information if shown.
11. Enter TIN, RSA PIN, PFA, NIN, and BVN if available.
12. Enter next of kin details.
13. Enter guarantor details.
14. Click Start onboarding.

### Expected Result

- Onboarding starts successfully.
- The page shows that onboarding has started for the candidate.
- The candidate is linked to a new employee record.
- The new employee can be found in Employees, at the side menu of the HR role.

## Step 18. Confirm The New Employee Record

**Where to go:** Employees, at the side menu of the HR role.

1. Open Employees from the HR side menu.
2. Search for the candidate name or email address.
3. Open the employee record if the list provides an open or view action.
4. Check the employee name, email, department, job step, pay group, manager, and personal details.

### Expected Result

- The employee record exists.
- The employee details match the accepted candidate and onboarding form.
- The recruitment application shows that the candidate has been converted to an employee.

## Step 19. Close The Job Opening

**Where to go:** Job openings, inside Recruitment at the side menu of the HR role. Then open the job.

1. Open Recruitment from the HR side menu.
2. Open Job openings.
3. Open the job used for this test.
4. Confirm the application count and filled positions are correct.
5. Find Close opening.
6. Click Close opening.

### Expected Result

- The job opening is closed.
- The job no longer accepts new applications.
- The job should no longer appear as an open role on the Careers page.

## Extra Checks

| Check | What To Do | Expected Result |
| --- | --- | --- |
| Required fields | Try to save each form with one important field left empty. | The form should not save and should show a clear message. |
| Duplicate candidate | Apply twice to the same job using the same candidate email address. | The system should either prevent the duplicate or show both records clearly if duplicates are allowed. |
| Closed job | Try to apply after the job is closed. | The job should not allow new applications. |
| Wrong user access | Sign in as an Employee and try to manage recruitment records. | The employee should not be able to manage recruitment records. |
| Finance offer approval | Sign in as Finance Officer and check if an offer waiting for finance approval is visible. | The Finance Officer should see only the offer approval items allowed for that role. |
| Screen size | Repeat the main pages on a phone sized screen. | Buttons, forms, and tables should remain readable and usable. |

## Final Pass Checklist

- **Done** Staff Requisition was created and approved.
- **Done** Job opening was created from the approved request.
- **Done** Job was prepared, reviewed, and published.
- **Done** Candidate applied from the Careers page.
- **Done** HR reviewed and advanced the candidate.
- **Done** Interview was scheduled and scored.
- **Done** Offer was created, approved, sent, and accepted.
- **Done** Candidate onboarding was started.
- **Done** New employee record was confirmed.
- **Done** Job opening was closed.
- **Log defects** Any failed step has a screenshot, user role, page name, button clicked, expected result, and actual result.
